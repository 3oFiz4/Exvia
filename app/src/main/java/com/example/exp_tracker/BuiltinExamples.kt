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

    /** Script templates run with d3, Plot, aq, jsonFile, context, theme, and helpers already initialized. */
    val customPlots: List<CustomPlotDefinition> = listOf(
        CustomPlotDefinition(
            id = "example_plot_scatter",
            name = "Example · Observable expense scatter",
            engine = "observable",
            enabled = false,
            script = """
                const rows = JSON.parse(jsonFile.content);
                if (!rows.length) return;
                const keys = Object.keys(rows[0]);
                const moneyKey = keys.find(k => ['price','amount','cost','expense','value','total','money'].includes(k.toLowerCase()));
                const dateKey = keys.find(k => ['date','datetime','timestamp','time','created_at','createdat'].includes(k.toLowerCase()));
                if (!moneyKey) throw new Error('No money-like key');
                const points = rows.map((r, i) => {
                  const parsed = dateKey ? helpers.parseDate(r[dateKey]) : null;
                  return {x: dateKey ? parsed : i, y: helpers.number(r[moneyKey]), label:String(r[dateKey] ?? i)};
                }).filter(d => (d.x instanceof Date ? !Number.isNaN(+d.x) : Number.isFinite(d.x)) && Number.isFinite(d.y));
                return Plot.plot({
                  width: context.width, height: context.height,
                  style: {background: theme.background, color: theme.text},
                  grid: true,
                  marks: [
                    Plot.dot(points, {x:'x', y:'y', fill:theme.observation, r:4, tip:true}),
                    Plot.linearRegressionY(points, {x:'x', y:'y', stroke:theme.accent})
                  ]
                });
            """.trimIndent(),
        ),
        CustomPlotDefinition(
            id = "example_plot_week_hour_heatmap",
            name = "Example · Observable weekday/hour heatmap",
            engine = "observable",
            enabled = false,
            script = """
                const rows = JSON.parse(jsonFile.content);
                if (!rows.length) return;
                const keys = Object.keys(rows[0]);
                const moneyKey = keys.find(k => ['price','amount','cost','expense','value','total','money'].includes(k.toLowerCase()));
                const dateKey = keys.find(k => ['date','datetime','timestamp','time','created_at','createdat'].includes(k.toLowerCase()));
                if (!moneyKey || !dateKey) throw new Error('Money/date key not found');
                const prepared = rows.map(r => {
                  const t = helpers.parseDate(r[dateKey]);
                  if (!t) return null;
                  return {day:t.toLocaleDateString(undefined,{weekday:'short'}), hour:t.getHours(), amount:Math.abs(helpers.number(r[moneyKey]))};
                }).filter(d => d && Number.isFinite(d.hour) && Number.isFinite(d.amount));
                const grouped = aq.from(prepared).groupby('day','hour').rollup({amount:d=>aq.op.sum(d.amount)}).objects();
                return Plot.plot({
                  width:context.width, height:context.height,
                  style:{background:theme.background,color:theme.text},
                  color:{scheme:'turbo'},
                  marks:[Plot.cell(grouped,{x:'hour',y:'day',fill:'amount',inset:1,tip:true})]
                });
            """.trimIndent(),
        ),
        CustomPlotDefinition(
            id = "example_plot_category_bar",
            name = "Example · Observable category totals",
            engine = "observable",
            enabled = false,
            script = """
                const rows = JSON.parse(jsonFile.content);
                if (!rows.length) return;
                const keys = Object.keys(rows[0]);
                const moneyKey = keys.find(k => ['price','amount','cost','expense','value','total','money'].includes(k.toLowerCase()));
                const categoryKey = keys.find(k => ['ticker','category','code','type'].includes(k.toLowerCase()));
                if (!moneyKey || !categoryKey) throw new Error('Money/category key not found');
                const prepared = rows.map(r => ({category:String(r[categoryKey] ?? 'Other'), amount:Math.abs(helpers.number(r[moneyKey]))})).filter(d=>Number.isFinite(d.amount));
                const totals = aq.from(prepared).groupby('category').rollup({amount:d=>aq.op.sum(d.amount)}).orderby(aq.desc('amount')).objects();
                return Plot.plot({
                  width:context.width,height:context.height,
                  marginLeft:80,style:{background:theme.background,color:theme.text},
                  marks:[Plot.barX(totals,{x:'amount',y:'category',sort:{y:'-x'},fill:theme.accent,tip:true}),Plot.ruleX([0])]
                });
            """.trimIndent(),
        ),
        CustomPlotDefinition(
            id = "example_plot_calendar_heatmap",
            name = "Example · D3 calendar heatmap",
            engine = "d3",
            enabled = false,
            script = """
                const rows = JSON.parse(jsonFile.content);
                if (!rows.length) return;
                const keys = Object.keys(rows[0]);
                const moneyKey = keys.find(k => ['price','amount','cost','expense','value','total','money'].includes(k.toLowerCase()));
                const dateKey = keys.find(k => ['date','datetime','timestamp','time','created_at','createdat'].includes(k.toLowerCase()));
                if (!moneyKey || !dateKey) throw new Error('Money/date key not found');
                const valid = rows.map(r => ({row:r, date:helpers.parseDate(r[dateKey])})).filter(d => d.date);
                const daily = d3.rollups(valid, v=>d3.sum(v,d=>Math.abs(helpers.number(d.row[moneyKey]))||0), d=>d3.timeDay(d.date))
                  .filter(d=>!Number.isNaN(+d[0])).sort((a,b)=>a[0]-b[0]);
                const cell=13, width=context.width, height=Math.max(170,Math.ceil(daily.length/7)*cell+40);
                const svg=d3.create('svg').attr('viewBox',[0,0,width,height]).style('background',theme.background).style('color',theme.text);
                const color=d3.scaleSequential(d3.interpolateTurbo).domain([0,d3.max(daily,d=>d[1])||1]);
                svg.selectAll('rect').data(daily).join('rect').attr('x',(d,i)=>22+Math.floor(i/7)*cell).attr('y',(d,i)=>18+(i%7)*cell)
                  .attr('width',cell-2).attr('height',cell-2).attr('rx',2).attr('fill',d=>color(d[1]))
                  .append('title').text(d=>`${'$'}{d3.timeFormat('%Y-%m-%d')(d[0])}: ${'$'}{helpers.format(d[1])}`);
                return svg.node();
            """.trimIndent(),
        ),
        CustomPlotDefinition(
            id = "example_plot_circle_pack",
            name = "Example · D3 category circle pack",
            engine = "d3",
            enabled = false,
            script = """
                const rows = JSON.parse(jsonFile.content);
                if (!rows.length) return;
                const keys = Object.keys(rows[0]);
                const moneyKey = keys.find(k => ['price','amount','cost','expense','value','total','money'].includes(k.toLowerCase()));
                const categoryKey = keys.find(k => ['ticker','category','code','type'].includes(k.toLowerCase()));
                if (!moneyKey || !categoryKey) throw new Error('Money/category key not found');
                const totals = d3.rollups(rows, v=>d3.sum(v,r=>Math.abs(helpers.number(r[moneyKey]))||0), r=>String(r[categoryKey]||'Other'));
                const root=d3.hierarchy({children:totals.map(([name,value])=>({name,value}))}).sum(d=>d.value||0);
                const size=Math.min(context.width,context.height); d3.pack().size([context.width,context.height]).padding(5)(root);
                const svg=d3.create('svg').attr('viewBox',[0,0,context.width,context.height]).style('background',theme.background);
                const nodes=svg.selectAll('g').data(root.leaves()).join('g').attr('transform',d=>`translate(${'$'}{d.x},${'$'}{d.y})`);
                nodes.append('circle').attr('r',d=>d.r).attr('fill',theme.accent).attr('fill-opacity',.75).attr('stroke',theme.center);
                nodes.append('text').attr('text-anchor','middle').attr('dy','.35em').attr('fill',theme.text).style('font-size','11px').text(d=>d.data.name);
                nodes.append('title').text(d=>`${'$'}{d.data.name}: ${'$'}{helpers.format(d.value)}`);
                return svg.node();
            """.trimIndent(),
        ),
    )

}
