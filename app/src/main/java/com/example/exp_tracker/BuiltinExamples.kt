package com.example.exp_tracker

object BuiltinExamples {
    val filterSnippets: List<FilterSnippet> = listOf(
        FilterSnippet(
            id = "example_date_month",
            name = "Dates in July 2026",
            query = "SELECT * WHERE REGEXP(date, '^[0-9]{1,2}/7/26(?:\\s*@.*)?\$')",
        ),
        FilterSnippet(
            id = "example_price_large",
            name = "Price at least 50",
            query = "SELECT * WHERE price >= 50",
        ),
        FilterSnippet(
            id = "example_price_income",
            name = "Income / +PRICE only",
            query = "SELECT * WHERE REGEXP(price, '^\\+')",
        ),
        FilterSnippet(
            id = "example_description_food",
            name = "Food-like descriptions",
            query = "SELECT * WHERE REGEXP(description, '(?i)food|lunch|dinner|restaurant|meal')",
        ),
    )

    /**
     * Templates intentionally receive only `jsonFile` from the host runtime.
     * The user performs JSON.parse(jsonFile.content) and infers whichever keys
     * they want to use inside their own script.
     */
    val customMetrics: List<CustomMetricDefinition> = listOf(
        CustomMetricDefinition(
            id = "example_metric_p90_expense",
            name = "Example · 90th Percentile Expense",
            enabled = false,
            script = """
                const rows = JSON.parse(jsonFile.content);
                if (!rows.length) return null;
                const keys = Object.keys(rows[0]);
                const moneyKey = keys.find(k => ['price','amount','cost','expense','value','total','money'].includes(k.toLowerCase()));
                if (!moneyKey) return 'No money-like key';
                const values = rows
                  .map(r => String(r[moneyKey] ?? '').trim())
                  .filter(v => v && !v.startsWith('+'))
                  .map(v => Number(v.replace(/,/g, '')))
                  .filter(Number.isFinite)
                  .map(Math.abs)
                  .sort((a,b) => a-b);
                if (!values.length) return null;
                const index = Math.max(0, Math.ceil(values.length * 0.90) - 1);
                return values[index];
            """.trimIndent(),
        ),
        CustomMetricDefinition(
            id = "example_metric_largest_spend_day",
            name = "Example · Largest Spending Day",
            enabled = false,
            script = """
                const rows = JSON.parse(jsonFile.content);
                if (!rows.length) return null;
                const keys = Object.keys(rows[0]);
                const moneyKey = keys.find(k => ['price','amount','cost','expense','value','total','money'].includes(k.toLowerCase()));
                const dateKey = keys.find(k => ['date','datetime','timestamp','time','created_at','createdat'].includes(k.toLowerCase()));
                if (!moneyKey || !dateKey) return 'Money/date key not found';
                const totals = {};
                for (const row of rows) {
                  const raw = String(row[moneyKey] ?? '').trim();
                  if (!raw || raw.startsWith('+')) continue;
                  const amount = Math.abs(Number(raw.replace(/,/g, '')));
                  if (!Number.isFinite(amount)) continue;
                  const date = String(row[dateKey] ?? '').split('@')[0].trim();
                  if (!date) continue;
                  totals[date] = (totals[date] || 0) + amount;
                }
                const entries = Object.entries(totals);
                if (!entries.length) return null;
                const best = entries.reduce((a,b) => b[1] > a[1] ? b : a);
                return {date: best[0], total: best[1]};
            """.trimIndent(),
        ),
        CustomMetricDefinition(
            id = "example_metric_longest_no_spend_streak",
            name = "Example · Longest No-Spend Streak",
            enabled = false,
            script = """
                const rows = JSON.parse(jsonFile.content);
                if (!rows.length) return null;
                const keys = Object.keys(rows[0]);
                const moneyKey = keys.find(k => ['price','amount','cost','expense','value','total','money'].includes(k.toLowerCase()));
                const dateKey = keys.find(k => ['date','datetime','timestamp','time','created_at','createdat'].includes(k.toLowerCase()));
                if (!moneyKey || !dateKey) return 'Money/date key not found';
                const parseDay = value => {
                  const m = String(value).match(/^(\d{1,2})\/(\d{1,2})\/(\d{2})/);
                  return m ? Math.floor(Date.UTC(2000 + Number(m[3]), Number(m[2]) - 1, Number(m[1])) / 86400000) : null;
                };
                const spendDays = rows.filter(r => {
                  const raw = String(r[moneyKey] ?? '').trim();
                  return raw && !raw.startsWith('+') && Number.isFinite(Number(raw.replace(/,/g, '')));
                }).map(r => parseDay(r[dateKey])).filter(v => v !== null);
                if (!spendDays.length) return null;
                const unique = [...new Set(spendDays)].sort((a,b) => a-b);
                let longest = 0;
                for (let i = 1; i < unique.length; i++) longest = Math.max(longest, unique[i] - unique[i-1] - 1);
                return longest;
            """.trimIndent(),
        ),
        CustomMetricDefinition(
            id = "example_metric_weekend_ratio",
            name = "Example · Weekend Spending Ratio",
            enabled = false,
            script = """
                const rows = JSON.parse(jsonFile.content);
                if (!rows.length) return null;
                const keys = Object.keys(rows[0]);
                const moneyKey = keys.find(k => ['price','amount','cost','expense','value','total','money'].includes(k.toLowerCase()));
                const dateKey = keys.find(k => ['date','datetime','timestamp','time','created_at','createdat'].includes(k.toLowerCase()));
                if (!moneyKey || !dateKey) return 'Money/date key not found';
                const weekday = value => {
                  const m = String(value).match(/^(\d{1,2})\/(\d{1,2})\/(\d{2})/);
                  return m ? new Date(Date.UTC(2000 + Number(m[3]), Number(m[2]) - 1, Number(m[1]))).getUTCDay() : null;
                };
                let total = 0, weekend = 0;
                for (const row of rows) {
                  const raw = String(row[moneyKey] ?? '').trim();
                  if (!raw || raw.startsWith('+')) continue;
                  const amount = Math.abs(Number(raw.replace(/,/g, '')));
                  if (!Number.isFinite(amount)) continue;
                  total += amount;
                  const day = weekday(row[dateKey]);
                  if (day === 0 || day === 6) weekend += amount;
                }
                return total > 0 ? weekend / total * 100 : null;
            """.trimIndent(),
        ),
        CustomMetricDefinition(
            id = "example_metric_category_hhi",
            name = "Example · Expense Category Concentration (HHI)",
            enabled = false,
            script = """
                const rows = JSON.parse(jsonFile.content);
                if (!rows.length) return null;
                const keys = Object.keys(rows[0]);
                const moneyKey = keys.find(k => ['price','amount','cost','expense','value','total','money'].includes(k.toLowerCase()));
                const categoryKey = keys.find(k => ['ticker','category','code','type'].includes(k.toLowerCase()));
                if (!moneyKey || !categoryKey) return 'Money/category key not found';
                const totals = {};
                let total = 0;
                for (const row of rows) {
                  const raw = String(row[moneyKey] ?? '').trim();
                  if (!raw || raw.startsWith('+')) continue;
                  const amount = Math.abs(Number(raw.replace(/,/g, '')));
                  if (!Number.isFinite(amount)) continue;
                  const category = String(row[categoryKey] ?? 'Uncategorized') || 'Uncategorized';
                  totals[category] = (totals[category] || 0) + amount;
                  total += amount;
                }
                if (!total) return null;
                return Object.values(totals).reduce((hhi, value) => hhi + Math.pow(value / total, 2), 0) * 10000;
            """.trimIndent(),
        ),
        CustomMetricDefinition(
            id = "example_metric_top_ten_share",
            name = "Example · Top 10% Expense Share",
            enabled = false,
            script = """
                const rows = JSON.parse(jsonFile.content);
                if (!rows.length) return null;
                const keys = Object.keys(rows[0]);
                const moneyKey = keys.find(k => ['price','amount','cost','expense','value','total','money'].includes(k.toLowerCase()));
                if (!moneyKey) return 'No money-like key';
                const values = rows
                  .map(r => String(r[moneyKey] ?? '').trim())
                  .filter(v => v && !v.startsWith('+'))
                  .map(v => Math.abs(Number(v.replace(/,/g, ''))))
                  .filter(Number.isFinite)
                  .sort((a,b) => b-a);
                if (!values.length) return null;
                const total = values.reduce((a,b) => a+b, 0);
                const topN = Math.max(1, Math.ceil(values.length * 0.10));
                const top = values.slice(0, topN).reduce((a,b) => a+b, 0);
                return total > 0 ? top / total * 100 : null;
            """.trimIndent(),
        ),
        CustomMetricDefinition(
            id = "example_metric_active_day_average",
            name = "Example · Average Spend per Active Day",
            enabled = false,
            script = """
                const rows = JSON.parse(jsonFile.content);
                if (!rows.length) return null;
                const keys = Object.keys(rows[0]);
                const moneyKey = keys.find(k => ['price','amount','cost','expense','value','total','money'].includes(k.toLowerCase()));
                const dateKey = keys.find(k => ['date','datetime','timestamp','time','created_at','createdat'].includes(k.toLowerCase()));
                if (!moneyKey || !dateKey) return 'Money/date key not found';
                const daily = {};
                for (const row of rows) {
                  const raw = String(row[moneyKey] ?? '').trim();
                  if (!raw || raw.startsWith('+')) continue;
                  const amount = Math.abs(Number(raw.replace(/,/g, '')));
                  if (!Number.isFinite(amount)) continue;
                  const day = String(row[dateKey] ?? '').split('@')[0].trim();
                  if (!day) continue;
                  daily[day] = (daily[day] || 0) + amount;
                }
                const values = Object.values(daily);
                return values.length ? values.reduce((a,b) => a+b, 0) / values.length : null;
            """.trimIndent(),
        ),
    )
}
