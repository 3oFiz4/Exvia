(() => {
  'use strict';

  const root = document.getElementById('plot-root');
  let lastPayload = null;

  const defaultTheme = Object.freeze({
    background:'#000000', surface:'#11151C', text:'#EDEDED', muted:'#7D7D7D',
    grid:'#1F1F1F', axis:'#7D7D7D', positive:'#34C759', negative:'#F72323',
    observation:'#3D8BFF', outlier:'#F72323', center:'#FFFFFF', accent:'#A970FF'
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
      return Math.abs(value) >= 1000 ? value.toLocaleString(undefined, {maximumFractionDigits:2}) : value.toFixed(3).replace(/\.?0+$/, '');
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
    attachZoom(node, maxScale = 40) {
      const wrapper = document.createElement('div');
      wrapper.style.cssText = 'width:100%;height:100%;overflow:hidden;touch-action:none;position:relative;';
      node.parentNode?.insertBefore(wrapper, node);
      wrapper.appendChild(node);
      node.style.transformOrigin = '0 0';
      const zoom = d3.zoom().scaleExtent([1, maxScale]).on('zoom', event => {
        const t = event.transform;
        node.style.transform = `translate(${t.x}px,${t.y}px) scale(${t.k})`;
      });
      d3.select(wrapper).call(zoom).on('dblclick.zoom', null);
      wrapper.addEventListener('dblclick', () => {
        d3.select(wrapper).transition().duration(120).call(zoom.transform, d3.zoomIdentity);
      });
      return wrapper;
    }
  });

  function themeOf(payload) { return Object.assign({}, defaultTheme, payload?.theme || {}); }

  function clear(theme) {
    root.replaceChildren();
    root.style.background = theme.background;
    document.body.style.background = theme.background;
    document.body.style.color = theme.text;
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
    tip.style.background = theme.surface;
    tip.style.color = theme.text;
    tip.style.borderColor = theme.accent;
    root.appendChild(tip);
    return tip;
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
        old.value += d.y; old.rowCount += 1; if (d.label) old.label = d.label;
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
      .style('font-size', '10px');
    axisGroup.selectAll('.tick text').each(function() {
      const parts = String(this.textContent).split('\n');
      if (parts.length > 1) {
        const label = d3.select(this).text(null);
        label.append('tspan').attr('x', 0).attr('dy', '0.7em').text(parts[0]);
        label.append('tspan').attr('x', 0).attr('dy', '1.15em').text(parts[1]);
      }
    });
  }

  function renderHistory(payload, theme) {
    const points = cumulativeBoxPoints(payload.data || []);
    if (!points.length) throw new Error('No dated numeric values for history plot.');
    clear(theme);
    const width = Math.max(320, root.clientWidth || 360);
    const height = Math.max(250, root.clientHeight || payload.height || 340);
    const margin = {top:28, right:24, bottom:58, left:64};
    const innerW = width - margin.left - margin.right;
    const innerH = height - margin.top - margin.bottom;
    const xValues = points.map(d => d.x);
    const xMin0 = d3.min(xValues), xMax0 = d3.max(xValues);
    const xSpan = Math.max(1, xMax0 - xMin0);
    const xPad = Math.max(xSpan * .06, points.length === 1 ? 30 * 60 * 1000 : 1);
    const ys = points.flatMap(d => [d.q1, d.q3, d.lowerStd, d.upperStd, d.value, ...d.outliers]);
    const yMin0 = d3.min(ys), yMax0 = d3.max(ys);
    const ySpan = Math.max(1e-9, yMax0 - yMin0);
    const yPad = Math.max(Math.abs(yMin0 || 0) * .02, Math.abs(yMax0 || 0) * .02, ySpan * .09, .5);
    const timeAxis = payload.timeAxis !== false;
    const x = (timeAxis ? d3.scaleTime() : d3.scaleLinear())
      .domain(timeAxis ? [new Date(xMin0 - xPad), new Date(xMax0 + xPad)] : [xMin0 - xPad, xMax0 + xPad])
      .range([0, innerW]);
    const xv = value => timeAxis ? new Date(value) : value;
    const y = d3.scaleLinear().domain([yMin0 - yPad, yMax0 + yPad]).nice().range([innerH, 0]);

    const svg = d3.create('svg')
      .attr('width', width).attr('height', height).attr('viewBox', [0, 0, width, height])
      .style('background', theme.background).style('color', theme.text)
      .style('touch-action', 'none');
    root.appendChild(svg.node());

    const clipId = `exvia-clip-${Math.random().toString(36).slice(2)}`;
    svg.append('defs').append('clipPath').attr('id', clipId)
      .append('rect').attr('width', innerW).attr('height', innerH);

    const plotFrame = svg.append('g')
      .attr('transform', `translate(${margin.left},${margin.top})`)
      .attr('clip-path', `url(#${clipId})`);
    plotFrame.append('rect')
      .attr('width', innerW).attr('height', innerH)
      .attr('fill', 'transparent').style('pointer-events', 'all');
    const plot = plotFrame.append('g');

    const grid = svg.append('g')
      .attr('transform', `translate(${margin.left},${margin.top})`)
      .attr('color', theme.grid);
    grid.call(d3.axisLeft(y).ticks(5).tickSize(-innerW).tickFormat(''))
      .call(group => group.select('.domain').remove())
      .call(group => group.selectAll('line').attr('stroke', theme.grid));

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
      .style('font-family', 'ExviaJetBrains, monospace');

    plot.append('path').datum(points)
      .attr('fill', 'none').attr('stroke', theme.observation)
      .attr('stroke-opacity', .40).attr('stroke-width', 1.7)
      .attr('d', d3.line().x(d => x(xv(d.x))).y(d => y(d.value)));

    const minGap = points.length > 1
      ? d3.min(d3.pairs(points, (a, b) => Math.abs(x(xv(b.x)) - x(xv(a.x)))))
      : 24;
    const boxW = Math.max(7, Math.min(22, (minGap || 24) * .52));
    const groups = plot.selectAll('g.snapshot').data(points).join('g')
      .attr('class', 'snapshot').attr('transform', d => `translate(${x(xv(d.x))},0)`);

    groups.each(function(d) {
      const group = d3.select(this);
      const color = d.direction > 0 ? theme.positive : d.direction < 0 ? theme.negative : theme.axis;
      const center = theme.center;
      group.append('line').attr('x1', 0).attr('x2', 0)
        .attr('y1', y(d.upperStd)).attr('y2', y(d.lowerStd))
        .attr('stroke', color).attr('stroke-width', 1.6);
      group.append('line').attr('x1', -boxW * .28).attr('x2', boxW * .28)
        .attr('y1', y(d.upperStd)).attr('y2', y(d.upperStd))
        .attr('stroke', color).attr('stroke-width', 1.6);
      group.append('line').attr('x1', -boxW * .28).attr('x2', boxW * .28)
        .attr('y1', y(d.lowerStd)).attr('y2', y(d.lowerStd))
        .attr('stroke', color).attr('stroke-width', 1.6);
      group.append('rect').attr('x', -boxW / 2).attr('width', boxW)
        .attr('y', y(d.q3)).attr('height', Math.max(1, y(d.q1) - y(d.q3)))
        .attr('fill', color).attr('stroke', color);
      group.append('line').attr('x1', -boxW / 2).attr('x2', boxW / 2)
        .attr('y1', y(d.median)).attr('y2', y(d.median))
        .attr('stroke', center).attr('stroke-width', 2.3);
      group.append('line').attr('x1', -boxW / 2).attr('x2', boxW / 2)
        .attr('y1', y(d.mean)).attr('y2', y(d.mean))
        .attr('stroke', center).attr('stroke-width', 1.9).attr('stroke-dasharray', '3,2');
      group.selectAll('circle.outlier').data(d.outliers).join('circle')
        .attr('class', 'outlier').attr('cx', 0).attr('cy', value => y(value))
        .attr('r', 1.9).attr('fill', 'none')
        .attr('stroke', theme.outlier).attr('stroke-width', 1.1);
      const observationY = y(d.value);
      group.append('path')
        .attr('d', `M -6 ${observationY} L 0 ${observationY - 4} L 6 ${observationY} L 0 ${observationY + 4} Z`)
        .attr('fill', theme.observation).attr('stroke', center).attr('stroke-width', .7);
    });

    const tip = tooltip(theme);
    groups.append('rect')
      .attr('x', -Math.max(10, boxW)).attr('width', Math.max(20, boxW * 2))
      .attr('y', 0).attr('height', innerH).attr('fill', 'transparent')
      .on('pointerenter pointermove', function(event, d) {
        tip.style.display = 'block';
        tip.textContent = `${d.label || new Date(d.x).toLocaleString()}\nTotal: ${helpers.format(d.value)} (${d.rowCount} row${d.rowCount === 1 ? '' : 's'})\nn: ${d.n}\nQ1: ${helpers.format(d.q1)}\nMedian: ${helpers.format(d.median)}\nMean: ${helpers.format(d.mean)}\nQ3: ${helpers.format(d.q3)}\nσ: ${helpers.format(d.stdv)}\nOutliers: ${d.outliers.length}`;
        const bounds = root.getBoundingClientRect();
        tip.style.left = Math.min(bounds.width - tip.offsetWidth - 6, event.clientX - bounds.left + 10) + 'px';
        tip.style.top = Math.max(4, event.clientY - bounds.top - tip.offsetHeight - 10) + 'px';
      })
      .on('pointerleave', () => tip.style.display = 'none');

    enableD3Zoom(plotFrame, plot, grid, xAxis, yAxis, x, y, timeAxis, innerW, innerH, theme);
  }

  function enableD3Zoom(plotFrame, plot, grid, xAxis, yAxis, x, y, timeAxis, innerW, innerH, theme) {
    const originalX = x.copy(), originalY = y.copy();
    const zoom = d3.zoom()
      .scaleExtent([1, 40])
      .translateExtent([[0, 0], [innerW, innerH]])
      .extent([[0, 0], [innerW, innerH]])
      .on('zoom', event => {
        const transformedX = event.transform.rescaleX(originalX);
        const transformedY = event.transform.rescaleY(originalY);
        plot.attr('transform', event.transform.toString());
        const nextXAxis = timeAxis
          ? d3.axisBottom(transformedX).ticks(5).tickFormat(d3.timeFormat('%-d/%-m/%y\n%H:%M'))
          : d3.axisBottom(transformedX).ticks(5);
        wrappedTimeAxis(xAxis, nextXAxis);
        yAxis.call(d3.axisLeft(transformedY).ticks(5));
        yAxis.selectAll('text').attr('fill', theme.text)
          .style('font-family', 'ExviaJetBrains, monospace');
        grid.call(d3.axisLeft(transformedY).ticks(5).tickSize(-innerW).tickFormat(''))
          .call(group => group.select('.domain').remove())
          .call(group => group.selectAll('line').attr('stroke', theme.grid));
      });
    plotFrame.call(zoom).on('dblclick.zoom', null);
    plotFrame.on('dblclick', () => {
      plotFrame.transition().duration(120).call(zoom.transform, d3.zoomIdentity);
    });
  }

  function plotBase(theme, width, height) {
    return {width,height,marginLeft:58,marginRight:20,marginTop:20,marginBottom:46,style:helpers.plotStyle(theme),x:{grid:true,label:null},y:{grid:true,label:null}};
  }

  function renderAccumulation(payload, theme) {
    const grouped=aggregateTimestamp(payload.data||[]);
    if(!grouped.length) throw new Error('No dated numeric values for accumulation plot.');
    let total=0; const data=grouped.map(d=>({...d,cumulative:(total+=d.value),date:new Date(d.x)}));
    clear(theme); const width=Math.max(320,root.clientWidth||360), height=Math.max(220,root.clientHeight||payload.height||230);
    const chart=Plot.plot({...plotBase(theme,width,height),x:{grid:true,type:payload.timeAxis===false?'linear':'utc'},marks:[
      Plot.ruleY([0],{stroke:theme.grid}),
      Plot.areaY(data,{x:payload.timeAxis===false?'x':'date',y:'cumulative',fill:theme.observation,fillOpacity:.12}),
      Plot.lineY(data,{x:payload.timeAxis===false?'x':'date',y:'cumulative',stroke:theme.observation,strokeWidth:2,tip:true}),
      Plot.dot(data,{x:payload.timeAxis===false?'x':'date',y:'cumulative',fill:theme.observation,r:2.7,tip:true})
    ]});
    root.appendChild(chart); helpers.attachZoom(chart);
  }

  function renderNormal(payload, theme) {
    const values=(payload.values||[]).map(Number).filter(Number.isFinite);
    if(!values.length) throw new Error('No numeric values for normal distribution.');
    const mean=d3.mean(values), deviation=Math.sqrt(d3.mean(values,v=>(v-mean)**2)||0);
    const spread=deviation>0?deviation:Math.max(Math.abs(mean)*.02,1);
    const min=d3.min(values), max=d3.max(values), lo=Math.min(min,mean-4*spread), hi=Math.max(max,mean+4*spread);
    const curve=d3.range(160).map(i=>{const x=lo+(hi-lo)*i/159; const z=(x-mean)/spread; return {x,density:Math.exp(-.5*z*z)/(spread*Math.sqrt(2*Math.PI))};});
    clear(theme); const width=Math.max(320,root.clientWidth||360), height=Math.max(220,root.clientHeight||payload.height||230);
    const chart=Plot.plot({...plotBase(theme,width,height),marks:[
      Plot.areaY(curve,{x:'x',y:'density',fill:theme.accent,fillOpacity:.14}),
      Plot.lineY(curve,{x:'x',y:'density',stroke:theme.accent,strokeWidth:2}),
      Plot.tickX(values,{x:d=>d,y:0,stroke:theme.observation,strokeOpacity:.55}),
      Plot.ruleX([mean],{stroke:theme.center,strokeDasharray:'4,3'}),
      Plot.tip(curve,Plot.pointerX({x:'x',y:'density',title:d=>`x ${helpers.format(d.x)}\ndensity ${helpers.format(d.density)}`}))
    ]});
    root.appendChild(chart); helpers.attachZoom(chart);
  }

  function renderCustom(payload, theme) {
    clear(theme);
    const jsonFile=Object.freeze(payload.jsonFile||{name:'current.json',content:'[]'});
    const context={container:root,width:Math.max(320,root.clientWidth||360),height:Math.max(220,root.clientHeight||payload.height||320),engine:payload.engine||'auto',theme,jsonFile,helpers};
    const fn=new Function('context','jsonFile','d3','Plot','aq','theme','helpers',`"use strict";\n${payload.script}\n`);
    const result=fn(context,jsonFile,d3,Plot,aq,theme,helpers);
    if(result && typeof result.then === 'function') throw new Error('Async custom plots are not supported; return synchronously.');
    if(result instanceof Node) root.appendChild(result);
    else if(result && typeof result.node==='function' && result.node() instanceof Node) root.appendChild(result.node());
    else if(result && result.node instanceof Node) root.appendChild(result.node);
    if(!root.childNodes.length) {
      const note=document.createElement('div'); note.className='exvia-error'; note.style.color=theme.muted; note.textContent='Custom plot returned no node and did not append to context.container.'; root.appendChild(note);
    }
  }

  function render(payload) {
    lastPayload=payload; const theme=themeOf(payload);
    try {
      if(typeof d3==='undefined'||typeof Plot==='undefined'||typeof aq==='undefined') throw new Error('Plot modules are not ready. Check network access, then re-open this plot.');
      if(payload.kind==='history') renderHistory(payload,theme);
      else if(payload.kind==='accumulation') renderAccumulation(payload,theme);
      else if(payload.kind==='normal') renderNormal(payload,theme);
      else if(payload.kind==='custom') renderCustom(payload,theme);
      else throw new Error(`Unknown plot kind: ${payload.kind}`);
      return JSON.stringify({ok:true});
    } catch(e) {
      error(e?.stack||e?.message||String(e),theme);
      return JSON.stringify({ok:false,error:String(e?.message||e)});
    }
  }

  function evaluateMetric(payload) {
    const theme=themeOf(payload);
    try {
      if(typeof d3==='undefined'||typeof Plot==='undefined'||typeof aq==='undefined') throw new Error('D3, Observable Plot, or Arquero is not ready.');
      const jsonFile=Object.freeze(payload.jsonFile||{name:'current.json',content:'[]'});
      const context=Object.freeze({theme,jsonFile,helpers});
      const fn=new Function('context','jsonFile','d3','Plot','aq','theme','helpers',`"use strict";\n${payload.script}\n`);
      const value=fn(context,jsonFile,d3,Plot,aq,theme,helpers);
      if(value && typeof value.then === 'function') throw new Error('Async custom metrics are not supported; return synchronously.');
      return JSON.stringify({ok:true,value:value===undefined?null:value});
    } catch(e) {
      return JSON.stringify({ok:false,error:String(e?.message||e)});
    }
  }

  window.ExviaRuntime=Object.freeze({clear:()=>{root.replaceChildren();lastPayload=null;},render,evaluateMetric,helpers,versions:()=>({d3:d3?.version||'7',plot:'0.6.17',arquero:'8.0.3'})});
  window.__EXVIA_READY__=true;
})();
