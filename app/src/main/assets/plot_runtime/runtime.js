(() => {
  'use strict';

  const root = document.getElementById('plot-root');
  let lastPayload = null;
  let isRendering = false;
  let resizeTimer = 0;
  let lastRootWidth = 0;
  let lastRootHeight = 0;

  const defaultTheme = Object.freeze({
    background:'#000000', surface:'#11151C', text:'#EDEDED', muted:'#7D7D7D',
    grid:'#1F1F1F', axis:'#7D7D7D', positive:'#34C759', negative:'#F72323',
    observation:'#3D8BFF', outlier:'#F72323', center:'#FFFFFF', accent:'#A970FF',
    selection:'#59C2FF', tooltipBackground:'#11151C', tooltipText:'#EDEDED', tooltipBorder:'#F72323'
  });

  const helpers = Object.freeze({
    number(value) {
      if (value === null || value === undefined) return NaN;
      const text = String(value).trim().replace(/,/g, '');
      if (!text) return NaN;
      const valueNumber = Number(text);
      return Number.isFinite(valueNumber) ? valueNumber : NaN;
    },
    parseDate(value) {
      if (value instanceof Date) return value;
      if (typeof value === 'number' && Number.isFinite(value)) return new Date(value);
      const text = String(value ?? '').trim();
      if (!text) return null;
      const custom = text.match(/^(\d{1,2})\/(\d{1,2})\/(\d{2})(?:\s*@\s*(\d{1,2}):(\d{2}))?$/);
      if (custom) {
        const year = 2000 + Number(custom[3]);
        const date = new Date(year, Number(custom[2]) - 1, Number(custom[1]), Number(custom[4] || 0), Number(custom[5] || 0));
        return Number.isNaN(+date) ? null : date;
      }
      const parsed = new Date(text);
      return Number.isNaN(+parsed) ? null : parsed;
    },
    inferKey(rows, aliases) {
      const keys = rows.length ? Object.keys(rows[0]) : [];
      const normalized = x => String(x).toLowerCase().replace(/[^a-z0-9_]/g, '');
      return aliases.map(normalized).map(alias => keys.find(k => normalized(k) === alias)).find(Boolean) || null;
    },
    plotStyle(theme) {
      return {
        background: theme.background,
        color: theme.text,
        fontFamily: 'ExviaJetBrains, monospace',
        fontSize: '11px'
      };
    },
    rows(jsonFile) {
      const parsed = JSON.parse(jsonFile.content || '[]');
      return Array.isArray(parsed) ? parsed : [];
    },
    table(jsonFile) {
      return aq.from(helpers.rows(jsonFile));
    },
    format(value) {
      if (!Number.isFinite(value)) return 'N/A';
      return Math.abs(value) >= 1000
        ? value.toLocaleString(undefined, {maximumFractionDigits:2})
        : value.toFixed(3).replace(/\.?0+$/, '');
    },
    statistics(values) {
      const clean = (values || []).map(helpers.number).filter(Number.isFinite).sort(d3.ascending);
      if (!clean.length) return {n:0, sum:0, mean:null, median:null, q1:null, q3:null, stdv:null, min:null, max:null};
      const mean = d3.mean(clean);
      return {
        n: clean.length,
        sum: d3.sum(clean),
        mean,
        median: d3.quantileSorted(clean, .5),
        q1: d3.quantileSorted(clean, .25),
        q3: d3.quantileSorted(clean, .75),
        stdv: Math.sqrt(d3.mean(clean, value => (value - mean) ** 2) || 0),
        min: clean[0],
        max: clean[clean.length - 1]
      };
    },
    timestampTotals(data) {
      return aggregateTimestamp(data || []);
    },
    cumulativeBoxes(data) {
      return cumulativeBoxPoints(data || []);
    },
    finance(rows, moneyKey) {
      let income = 0, expenses = 0;
      for (const row of rows || []) {
        const raw = String(row?.[moneyKey] ?? '').trim();
        const value = Math.abs(helpers.number(raw));
        if (!Number.isFinite(value)) continue;
        if (raw.startsWith('+')) income += value; else expenses += value;
      }
      const netCashFlow = income - expenses;
      return {
        income, expenses, netCashFlow,
        savingsRate: income > 0 ? netCashFlow / income * 100 : null,
        expenseRatio: income > 0 ? expenses / income * 100 : null
      };
    },
    /**
     * Zooms the SVG by changing its viewBox rather than applying a CSS transform
     * to only one mark layer. This enlarges the complete rendered graph—axes,
     * labels, symbols, strokes and marks—while keeping the Android/WebView size
     * stable. Pointer coordinates continue to map through getScreenCTM().
     */
    attachZoom(node, maxScale = 40, onZoomStart = null) {
      const svg = node?.matches?.('svg') ? node : node?.querySelector?.('svg');
      if (!svg) return node;
      const host = svg.parentElement || root;
      const selection = d3.select(svg);
      const rawViewBox = (svg.getAttribute('viewBox') || '').trim().split(/[ ,]+/).map(Number);
      const baseWidth = Number.isFinite(rawViewBox[2]) && rawViewBox[2] > 0
        ? rawViewBox[2]
        : (svg.width?.baseVal?.value || svg.clientWidth || host.clientWidth || 360);
      const baseHeight = Number.isFinite(rawViewBox[3]) && rawViewBox[3] > 0
        ? rawViewBox[3]
        : (svg.height?.baseVal?.value || svg.clientHeight || host.clientHeight || 240);
      const baseX = Number.isFinite(rawViewBox[0]) ? rawViewBox[0] : 0;
      const baseY = Number.isFinite(rawViewBox[1]) ? rawViewBox[1] : 0;
      const baseViewBox = `${baseX} ${baseY} ${baseWidth} ${baseHeight}`;

      selection
        .attr('viewBox', baseViewBox)
        .attr('preserveAspectRatio', 'none')
        .style('width', '100%')
        .style('height', '100%')
        .style('max-width', 'none')
        .style('touch-action', 'none');
      host.style.touchAction = 'none';
      host.style.overflow = 'hidden';

      const hostSize = () => ({
        width: Math.max(1, host.clientWidth || svg.clientWidth || baseWidth),
        height: Math.max(1, host.clientHeight || svg.clientHeight || baseHeight)
      });

      const applyTransform = transform => {
        const size = hostSize();
        const unitX = baseWidth / size.width;
        const unitY = baseHeight / size.height;
        const viewX = baseX - (transform.x * unitX) / transform.k;
        const viewY = baseY - (transform.y * unitY) / transform.k;
        const viewWidth = baseWidth / transform.k;
        const viewHeight = baseHeight / transform.k;
        selection.attr('viewBox', `${viewX} ${viewY} ${viewWidth} ${viewHeight}`);
        svg.dataset.exviaZoomScale = String(transform.k);
      };

      const initialHostSize = hostSize();
      const zoom = d3.zoom()
        .scaleExtent([1, maxScale])
        .extent([[0, 0], [initialHostSize.width, initialHostSize.height]])
        .translateExtent([[0, 0], [initialHostSize.width, initialHostSize.height]])
        .on('start.exvia', () => onZoomStart?.())
        .on('zoom.exvia', event => applyTransform(event.transform));

      const hostSelection = d3.select(host);
      hostSelection.on('.zoom', null).call(zoom).on('dblclick.zoom', null);
      hostSelection.on('dblclick.exvia-reset', event => {
        event.preventDefault();
        hostSelection.transition().duration(120).call(zoom.transform, d3.zoomIdentity);
      });
      applyTransform(d3.zoomIdentity);
      return host;
    }
  });

  function themeOf(payload) { return Object.assign({}, defaultTheme, payload?.theme || {}); }

  function applyTheme(theme) {
    const style = document.documentElement.style;
    style.setProperty('--exvia-tooltip-bg', theme.tooltipBackground || theme.surface);
    style.setProperty('--exvia-tooltip-text', theme.tooltipText || theme.text);
    style.setProperty('--exvia-tooltip-border', theme.tooltipBorder || theme.accent);
    root.style.background = theme.background;
    document.body.style.background = theme.background;
    document.body.style.color = theme.text;
  }

  function clear(theme) {
    root.replaceChildren();
    applyTheme(theme);
  }

  function error(message, theme) {
    clear(theme);
    const div = document.createElement('div');
    div.className = 'exvia-error';
    div.textContent = String(message);
    root.appendChild(div);
  }

  function tooltip(theme) {
    const tip = document.createElement('div');
    tip.className = 'exvia-tooltip';
    tip.style.display = 'none';
    tip.style.background = theme.tooltipBackground || theme.surface;
    tip.style.color = theme.tooltipText || theme.text;
    tip.style.borderColor = theme.tooltipBorder || theme.accent;
    root.appendChild(tip);
    return tip;
  }

  function placeTooltip(tip, event) {
    tip.style.display = 'block';
    const bounds = root.getBoundingClientRect();
    const localX = event.clientX - bounds.left;
    const localY = event.clientY - bounds.top;
    const width = tip.offsetWidth;
    const height = tip.offsetHeight;
    tip.style.left = Math.max(4, Math.min(bounds.width - width - 4, localX + 9)) + 'px';
    const above = localY - height - 10;
    tip.style.top = (above >= 4 ? above : Math.min(bounds.height - height - 4, localY + 10)) + 'px';
  }

  function aggregateTimestamp(data) {
    const clean = data.map(d => ({x:+d.x, y:+d.y, label:String(d.label ?? '')}))
      .filter(d => Number.isFinite(d.x) && Number.isFinite(d.y));
    if (!clean.length) return [];
    try {
      const labels = new Map(clean.map(d => [d.x, d.label]));
      return aq.from(clean)
        .groupby('x')
        .rollup({value:d => aq.op.sum(d.y), rowCount:d => aq.op.count()})
        .orderby('x')
        .objects()
        .map(d => ({x:+d.x, value:+d.value, rowCount:+d.rowCount, label:labels.get(+d.x) || ''}));
    } catch (_) {
      const map = new Map();
      clean.forEach(d => {
        const old = map.get(d.x) || {x:d.x, value:0, rowCount:0, label:d.label};
        old.value += d.y;
        old.rowCount += 1;
        if (d.label) old.label = d.label;
        map.set(d.x, old);
      });
      return [...map.values()].sort((a,b) => a.x-b.x);
    }
  }

  function cumulativeBoxPoints(data) {
    const grouped = aggregateTimestamp(data);
    const prefix = [];
    return grouped.map((d, i) => {
      prefix.push(d.value);
      const sorted = prefix.slice().sort(d3.ascending);
      const q1 = d3.quantileSorted(sorted, .25);
      const median = d3.quantileSorted(sorted, .5);
      const q3 = d3.quantileSorted(sorted, .75);
      const mean = d3.mean(prefix);
      const variance = d3.mean(prefix, v => (v - mean) ** 2) || 0;
      const stdv = Math.sqrt(variance);
      const iqr = q3 - q1;
      const lowFence = q1 - 1.5 * iqr;
      const highFence = q3 + 1.5 * iqr;
      return {
        ...d, q1, median, q3, mean, stdv,
        lowerStd:mean-stdv, upperStd:mean+stdv,
        outliers:prefix.filter(v => v < lowFence || v > highFence),
        direction:i === 0 ? 0 : (d.value > grouped[i-1].value ? 1 : -1),
        n:prefix.length
      };
    });
  }

  function wrappedTimeAxis(axisGroup, axis) {
    axisGroup.call(axis);
    axisGroup.selectAll('text')
      .attr('fill', axisGroup.attr('data-text-color') || null)
      .style('font-family', 'ExviaJetBrains, monospace')
      .style('font-size', '9px');
    axisGroup.selectAll('.tick text').each(function() {
      const parts = String(this.textContent).split('\n');
      if (parts.length > 1) {
        const label = d3.select(this).text(null);
        label.append('tspan').attr('x', 0).attr('dy', '0.7em').text(parts[0]);
        label.append('tspan').attr('x', 0).attr('dy', '1.12em').text(parts[1]);
      }
    });
  }

  function renderHistory(payload, theme) {
    const points = cumulativeBoxPoints(payload.data || []);
    if (!points.length) throw new Error('No dated numeric values for history plot.');
    clear(theme);
    const width = Math.max(280, root.clientWidth || 360);
    const height = Math.max(230, root.clientHeight || payload.height || 320);
    const margin = {top:18, right:8, bottom:52, left:50};
    const innerW = Math.max(80, width - margin.left - margin.right);
    const innerH = Math.max(80, height - margin.top - margin.bottom);
    const xValues = points.map(d => d.x);
    const xMin0 = d3.min(xValues);
    const xMax0 = d3.max(xValues);
    const timeAxis = payload.timeAxis !== false;
    const singleHalfSpan = timeAxis ? 30 * 60 * 1000 : 1;
    const domainMin = xMin0 === xMax0 ? xMin0 - singleHalfSpan : xMin0;
    const domainMax = xMin0 === xMax0 ? xMax0 + singleHalfSpan : xMax0;
    const edgePx = Math.min(9, Math.max(5, innerW * .018));
    const ys = points.flatMap(d => [d.q1, d.q3, d.lowerStd, d.upperStd, d.value, ...d.outliers]);
    const yMin0 = d3.min(ys);
    const yMax0 = d3.max(ys);
    const ySpan = Math.max(1e-9, yMax0 - yMin0);
    const yPad = Math.max(Math.abs(yMin0 || 0) * .012, Math.abs(yMax0 || 0) * .012, ySpan * .055, .25);
    const x = (timeAxis ? d3.scaleTime() : d3.scaleLinear())
      .domain(timeAxis ? [new Date(domainMin), new Date(domainMax)] : [domainMin, domainMax])
      .range([edgePx, innerW - edgePx]);
    const xv = value => timeAxis ? new Date(value) : value;
    const y = d3.scaleLinear().domain([yMin0 - yPad, yMax0 + yPad]).nice().range([innerH, 0]);

    const svg = d3.create('svg')
      .attr('width', width)
      .attr('height', height)
      .attr('viewBox', [0, 0, width, height])
      .attr('preserveAspectRatio', 'none')
      .style('width', '100%')
      .style('height', '100%')
      .style('background', theme.background)
      .style('color', theme.text)
      .style('touch-action', 'none');
    root.appendChild(svg.node());

    const clipId = `exvia-clip-${Math.random().toString(36).slice(2)}`;
    svg.append('defs').append('clipPath').attr('id', clipId)
      .append('rect').attr('width', innerW).attr('height', innerH);

    const plotFrame = svg.append('g')
      .attr('transform', `translate(${margin.left},${margin.top})`)
      .attr('clip-path', `url(#${clipId})`);
    const interactionRect = plotFrame.append('rect')
      .attr('width', innerW).attr('height', innerH)
      .attr('fill', 'transparent').style('pointer-events', 'all');
    const plot = plotFrame.append('g').attr('class', 'zoom-content');

    const grid = svg.append('g')
      .attr('transform', `translate(${margin.left},${margin.top})`)
      .attr('color', theme.grid);
    grid.call(d3.axisLeft(y).ticks(5).tickSize(-innerW).tickFormat(''))
      .call(group => group.select('.domain').remove())
      .call(group => group.selectAll('line').attr('stroke', theme.grid).attr('stroke-width', .7));

    const xAxis = svg.append('g')
      .attr('transform', `translate(${margin.left},${height - margin.bottom})`)
      .attr('color', theme.axis)
      .attr('data-text-color', theme.text);
    const initialXAxis = timeAxis
      ? d3.axisBottom(x).ticks(Math.min(5, points.length)).tickFormat(d3.timeFormat('%-d/%-m/%y\n%H:%M'))
      : d3.axisBottom(x).ticks(5);
    wrappedTimeAxis(xAxis, initialXAxis);

    const yAxis = svg.append('g')
      .attr('transform', `translate(${margin.left},${margin.top})`)
      .attr('color', theme.axis)
      .call(d3.axisLeft(y).ticks(5));
    yAxis.selectAll('text').attr('fill', theme.text)
      .style('font-family', 'ExviaJetBrains, monospace')
      .style('font-size', '9px');

    plot.append('path').datum(points)
      .attr('fill', 'none').attr('stroke', theme.observation)
      .attr('stroke-opacity', .40).attr('stroke-width', 1.05)
      .attr('d', d3.line().x(d => x(xv(d.x))).y(d => y(d.value)));

    const minGap = points.length > 1
      ? d3.min(d3.pairs(points, (a, b) => Math.abs(x(xv(b.x)) - x(xv(a.x)))))
      : 18;
    const boxW = Math.max(3, Math.min(10, (minGap || 18) * .28));
    const hitW = Math.max(18, Math.min(34, (minGap || 24) * .78));
    const groups = plot.selectAll('g.snapshot').data(points).join('g')
      .attr('class', 'snapshot').attr('transform', d => `translate(${x(xv(d.x))},0)`);

    groups.each(function(d) {
      const group = d3.select(this);
      const color = d.direction > 0 ? theme.positive : d.direction < 0 ? theme.negative : theme.axis;
      const center = theme.center;
      group.append('line').attr('x1', 0).attr('x2', 0)
        .attr('y1', y(d.upperStd)).attr('y2', y(d.lowerStd))
        .attr('stroke', color).attr('stroke-width', .85);
      group.append('line').attr('x1', -boxW * .26).attr('x2', boxW * .26)
        .attr('y1', y(d.upperStd)).attr('y2', y(d.upperStd))
        .attr('stroke', color).attr('stroke-width', .85);
      group.append('line').attr('x1', -boxW * .26).attr('x2', boxW * .26)
        .attr('y1', y(d.lowerStd)).attr('y2', y(d.lowerStd))
        .attr('stroke', color).attr('stroke-width', .85);
      group.append('rect').attr('x', -boxW / 2).attr('width', boxW)
        .attr('y', y(d.q3)).attr('height', Math.max(.7, y(d.q1) - y(d.q3)))
        .attr('fill', color).attr('stroke', color).attr('stroke-width', .45);
      group.append('line').attr('x1', -boxW / 2).attr('x2', boxW / 2)
        .attr('y1', y(d.median)).attr('y2', y(d.median))
        .attr('stroke', center).attr('stroke-width', 1.25);
      group.append('line').attr('x1', -boxW / 2).attr('x2', boxW / 2)
        .attr('y1', y(d.mean)).attr('y2', y(d.mean))
        .attr('stroke', center).attr('stroke-width', .9).attr('stroke-dasharray', '1.7,1.5');
      group.selectAll('circle.outlier').data(d.outliers).join('circle')
        .attr('class', 'outlier').attr('cx', 0).attr('cy', value => y(value))
        .attr('r', 1.1).attr('fill', 'none')
        .attr('stroke', theme.outlier).attr('stroke-width', .75);
      const observationY = y(d.value);
      group.append('path')
        .attr('class', 'observation-node')
        .attr('d', `M -3.5 ${observationY} L 0 ${observationY - 2.25} L 3.5 ${observationY} L 0 ${observationY + 2.25} Z`)
        .attr('fill', theme.observation).attr('stroke', center).attr('stroke-width', .45);
      group.append('rect')
        .attr('class', 'hit-zone')
        .attr('x', -hitW / 2).attr('width', hitW)
        .attr('y', 0).attr('height', innerH)
        .attr('fill', 'transparent').style('pointer-events', 'all');
    });

    const tip = tooltip(theme);
    let selectedNode = null;
    let pressStart = null;
    const hideTip = () => {
      tip.style.display = 'none';
      if (selectedNode) {
        d3.select(selectedNode).select('.observation-node')
          .attr('stroke', theme.center).attr('stroke-width', .45);
      }
      selectedNode = null;
    };
    const selectPoint = (event, d, node) => {
      if (selectedNode && selectedNode !== node) {
        d3.select(selectedNode).select('.observation-node')
          .attr('stroke', theme.center).attr('stroke-width', .45);
      }
      selectedNode = node;
      d3.select(node).select('.observation-node')
        .attr('stroke', theme.selection).attr('stroke-width', 1.25);
      tip.textContent = `${d.label || new Date(d.x).toLocaleString()}\nTotal: ${helpers.format(d.value)} (${d.rowCount} row${d.rowCount === 1 ? '' : 's'})\nn: ${d.n}\nQ1: ${helpers.format(d.q1)}\nMedian: ${helpers.format(d.median)}\nMean: ${helpers.format(d.mean)}\nQ3: ${helpers.format(d.q3)}\nσ: ${helpers.format(d.stdv)}\nOutliers: ${d.outliers.length}`;
      placeTooltip(tip, event);
    };

    groups
      .on('pointerdown.tooltip', function(event) {
        pressStart = {x:event.clientX, y:event.clientY, time:performance.now(), node:this};
      })
      .on('pointerup.tooltip', function(event, d) {
        if (!pressStart || pressStart.node !== this) return;
        const moved = Math.hypot(event.clientX - pressStart.x, event.clientY - pressStart.y);
        const elapsed = performance.now() - pressStart.time;
        pressStart = null;
        if (moved <= 10 && elapsed <= 900) {
          event.preventDefault();
          event.stopPropagation();
          if (selectedNode === this && tip.style.display !== 'none') hideTip();
          else selectPoint(event, d, this);
        }
      })
      .on('pointercancel.tooltip', () => { pressStart = null; });

    interactionRect.on('pointerup.tooltip', event => {
      if (event.target === interactionRect.node()) hideTip();
    });

    enableD3Zoom(svg.node(), hideTip);
  }

  function enableD3Zoom(svgNode, onZoomStart) {
    helpers.attachZoom(svgNode, 40, onZoomStart);
  }

  function plotBase(theme, width, height) {
    return {
      width, height,
      marginLeft:48, marginRight:8, marginTop:12, marginBottom:40,
      style:helpers.plotStyle(theme),
      x:{grid:true,label:null}, y:{grid:true,label:null}
    };
  }

  function installObservableInspector(chart, data, options, theme) {
    const svg = chart.matches?.('svg') ? chart : chart.querySelector?.('svg');
    if (!svg || !data.length) return;
    const tip = tooltip(theme);
    const width = options.width;
    const height = options.height;
    const marginLeft = options.marginLeft;
    const marginRight = options.marginRight;
    const marginTop = options.marginTop;
    const marginBottom = options.marginBottom;
    const xValues = data.map(options.xValue);
    const xDomain = d3.extent(xValues);
    const sameX = +xDomain[0] === +xDomain[1];
    const xScale = options.time
      ? d3.scaleUtc().domain(sameX ? [new Date(+xDomain[0] - 1800000), new Date(+xDomain[1] + 1800000)] : xDomain).range([marginLeft, width - marginRight])
      : d3.scaleLinear().domain(sameX ? [+xDomain[0] - 1, +xDomain[1] + 1] : xDomain).range([marginLeft, width - marginRight]);
    const overlay = d3.select(svg).append('rect')
      .attr('x', marginLeft).attr('y', marginTop)
      .attr('width', Math.max(1, width - marginLeft - marginRight))
      .attr('height', Math.max(1, height - marginTop - marginBottom))
      .attr('fill', 'transparent').style('pointer-events', 'all');
    let start = null;
    overlay
      .on('pointerdown.inspect', event => { start = {x:event.clientX, y:event.clientY, time:performance.now()}; })
      .on('pointerup.inspect', event => {
        if (!start) return;
        const moved = Math.hypot(event.clientX - start.x, event.clientY - start.y);
        const elapsed = performance.now() - start.time;
        start = null;
        if (moved > 10 || elapsed > 900) return;
        const point = svg.createSVGPoint();
        point.x = event.clientX;
        point.y = event.clientY;
        const matrix = svg.getScreenCTM();
        if (!matrix) return;
        const local = point.matrixTransform(matrix.inverse());
        const target = xScale.invert(local.x);
        const nearest = d3.least(data, d => Math.abs(+options.xValue(d) - +target));
        if (!nearest) return;
        tip.textContent = options.label(nearest);
        placeTooltip(tip, event);
      })
      .on('pointercancel.inspect', () => { start = null; });
  }

  function renderAccumulation(payload, theme) {
    const grouped = aggregateTimestamp(payload.data || []);
    if (!grouped.length) throw new Error('No dated numeric values for accumulation plot.');
    let total = 0;
    const timeAxis = payload.timeAxis !== false;
    const data = grouped.map(d => ({...d, cumulative:(total += d.value), date:new Date(d.x)}));
    clear(theme);
    const width = Math.max(280, root.clientWidth || 360);
    const height = Math.max(210, root.clientHeight || payload.height || 230);
    const base = plotBase(theme, width, height);
    const xField = timeAxis ? 'date' : 'x';
    const chart = Plot.plot({...base, x:{grid:true,type:timeAxis ? 'utc' : 'linear'}, marks:[
      Plot.ruleY([0], {stroke:theme.grid}),
      Plot.areaY(data, {x:xField,y:'cumulative',fill:theme.observation,fillOpacity:.12}),
      Plot.lineY(data, {x:xField,y:'cumulative',stroke:theme.observation,strokeWidth:1.35}),
      Plot.dot(data, {x:xField,y:'cumulative',fill:theme.observation,stroke:theme.center,strokeWidth:.35,r:2.15})
    ]});
    root.appendChild(chart);
    installObservableInspector(chart, data, {
      ...base,
      time:timeAxis,
      xValue:d => timeAxis ? d.date : d.x,
      label:d => `${d.label || (timeAxis ? d.date.toLocaleString() : helpers.format(d.x))}\nTimestamp total: ${helpers.format(d.value)}\nAccumulation: ${helpers.format(d.cumulative)}`
    }, theme);
    helpers.attachZoom(chart);
  }

  function renderNormal(payload, theme) {
    const values = (payload.values || []).map(Number).filter(Number.isFinite);
    if (!values.length) throw new Error('No numeric values for normal distribution.');
    const mean = d3.mean(values);
    const deviation = Math.sqrt(d3.mean(values, v => (v - mean) ** 2) || 0);
    const spread = deviation > 0 ? deviation : Math.max(Math.abs(mean) * .02, 1);
    const min = d3.min(values);
    const max = d3.max(values);
    const lo = Math.min(min, mean - 4 * spread);
    const hi = Math.max(max, mean + 4 * spread);
    const curve = d3.range(160).map(i => {
      const x = lo + (hi - lo) * i / 159;
      const z = (x - mean) / spread;
      return {x, density:Math.exp(-.5 * z * z) / (spread * Math.sqrt(2 * Math.PI))};
    });
    clear(theme);
    const width = Math.max(280, root.clientWidth || 360);
    const height = Math.max(210, root.clientHeight || payload.height || 230);
    const base = plotBase(theme, width, height);
    const chart = Plot.plot({...base, marks:[
      Plot.areaY(curve, {x:'x',y:'density',fill:theme.accent,fillOpacity:.14}),
      Plot.lineY(curve, {x:'x',y:'density',stroke:theme.accent,strokeWidth:1.4}),
      Plot.tickX(values, {x:d => d,y:0,stroke:theme.observation,strokeOpacity:.55,strokeWidth:.7}),
      Plot.ruleX([mean], {stroke:theme.center,strokeDasharray:'3,3',strokeWidth:.9})
    ]});
    root.appendChild(chart);
    installObservableInspector(chart, curve, {
      ...base,
      time:false,
      xValue:d => d.x,
      label:d => `x: ${helpers.format(d.x)}\nDensity: ${helpers.format(d.density)}\nMean: ${helpers.format(mean)}\nσ: ${helpers.format(deviation)}`
    }, theme);
    helpers.attachZoom(chart);
  }

  function renderCustom(payload, theme) {
    clear(theme);
    const jsonFile = Object.freeze(payload.jsonFile || {name:'current.json',content:'[]'});
    const context = {
      container:root,
      width:Math.max(280, root.clientWidth || 360),
      height:Math.max(210, root.clientHeight || payload.height || 320),
      engine:payload.engine || 'auto',
      theme, jsonFile, helpers
    };
    const fn = new Function('context','jsonFile','d3','Plot','aq','theme','helpers',`"use strict";\n${payload.script}\n`);
    const result = fn(context,jsonFile,d3,Plot,aq,theme,helpers);
    if (result && typeof result.then === 'function') throw new Error('Async custom plots are not supported; return synchronously.');
    if (result instanceof Node) root.appendChild(result);
    else if (result && typeof result.node === 'function' && result.node() instanceof Node) root.appendChild(result.node());
    else if (result && result.node instanceof Node) root.appendChild(result.node);
    if (!root.childNodes.length) {
      const note = document.createElement('div');
      note.className = 'exvia-error';
      note.style.color = theme.muted;
      note.textContent = 'Custom plot returned no node and did not append to context.container.';
      root.appendChild(note);
    }
  }

  function render(payload) {
    lastPayload = payload;
    const theme = themeOf(payload);
    isRendering = true;
    try {
      if (typeof d3 === 'undefined' || typeof Plot === 'undefined' || typeof aq === 'undefined') {
        throw new Error('Plot modules are not ready. Check network access, then re-open this plot.');
      }
      if (payload.kind === 'history') renderHistory(payload, theme);
      else if (payload.kind === 'accumulation') renderAccumulation(payload, theme);
      else if (payload.kind === 'normal') renderNormal(payload, theme);
      else if (payload.kind === 'custom') renderCustom(payload, theme);
      else throw new Error(`Unknown plot kind: ${payload.kind}`);
      lastRootWidth = root.clientWidth;
      lastRootHeight = root.clientHeight;
      return JSON.stringify({ok:true});
    } catch (e) {
      error(e?.stack || e?.message || String(e), theme);
      return JSON.stringify({ok:false,error:String(e?.message || e)});
    } finally {
      isRendering = false;
    }
  }

  function evaluateMetric(payload) {
    const theme = themeOf(payload);
    try {
      if (typeof d3 === 'undefined' || typeof Plot === 'undefined' || typeof aq === 'undefined') {
        throw new Error('D3, Observable Plot, or Arquero is not ready.');
      }
      const jsonFile = Object.freeze(payload.jsonFile || {name:'current.json',content:'[]'});
      const context = Object.freeze({theme,jsonFile,helpers});
      const fn = new Function('context','jsonFile','d3','Plot','aq','theme','helpers',`"use strict";\n${payload.script}\n`);
      const value = fn(context,jsonFile,d3,Plot,aq,theme,helpers);
      if (value && typeof value.then === 'function') throw new Error('Async custom metrics are not supported; return synchronously.');
      return JSON.stringify({ok:true,value:value === undefined ? null : value});
    } catch (e) {
      return JSON.stringify({ok:false,error:String(e?.message || e)});
    }
  }

  function resize() {
    if (!lastPayload || isRendering) return;
    const width = root.clientWidth;
    const height = root.clientHeight;
    if (Math.abs(width - lastRootWidth) <= 2 && Math.abs(height - lastRootHeight) <= 2) return;
    render(lastPayload);
  }

  if (typeof ResizeObserver !== 'undefined') {
    new ResizeObserver(() => {
      if (!lastPayload || isRendering) return;
      window.clearTimeout(resizeTimer);
      resizeTimer = window.setTimeout(resize, 60);
    }).observe(document.documentElement);
  }

  window.ExviaRuntime = Object.freeze({
    clear:() => { root.replaceChildren(); lastPayload = null; },
    render,
    resize,
    evaluateMetric,
    helpers,
    versions:() => ({d3:d3?.version || '7', plot:'0.6.17', arquero:'8.0.3'})
  });
  window.__EXVIA_READY__ = true;
})();
