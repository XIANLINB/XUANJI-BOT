function r(t,e,c){const a=new Blob([t],{type:c}),o=URL.createObjectURL(a),n=document.createElement("a");n.href=o,n.download=e,document.body.appendChild(n),n.click(),n.remove(),URL.revokeObjectURL(o)}function s(t){const e=t==null?"":String(t);return e.includes(",")||e.includes('"')||e.includes(`
`)||e.includes("\r")?'"'+e.replace(/"/g,'""')+'"':e}function i(t,e,c){const a=e.map(n=>s(n.label)).join(","),o=t.map(n=>e.map(l=>s(l.value?l.value(n):n[l.key])).join(","));r("\uFEFF"+a+`\r
`+o.join(`\r
`),c,"text/csv;charset=utf-8")}function u(t,e){r(JSON.stringify(t,null,2),e,"application/json;charset=utf-8")}export{i as a,u as e};
