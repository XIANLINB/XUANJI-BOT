import{c as s,b as n,d as _,f as S,h as R,u as V,aI as D,z as d,A as L,B as f,a4 as G,D as l,G as u,H as o,K as A,O as H,Q as y,S as x,V as i,X as k,aJ as U,P as N,R as C,aK as W,T as F,_ as J}from"./index-CniGiLyP.js";import{_ as z}from"./DataTable.vue_vue_type_script_setup_true_lang-HEAt3xaC.js";import{u as X}from"./useFillHeight-BJvsrN-8.js";import{N as j}from"./Select-CwYV4pcW.js";import{N as I}from"./Alert-_i42cW2E.js";import{N as Y}from"./Empty-kAFF6Qt4.js";import{N as Z}from"./Input-DpnKKtKH.js";import"./DataTable-CMh7A4qF.js";import"./RadioGroup-BlognMUu.js";import"./get-slot-Bk_rJcZu.js";import"./use-locale-Bn7lCNC0.js";import"./Tag-BCQAS3gm.js";import"./Checkmark-DgyyzcuI.js";const tt=s("input-group",`
 display: inline-flex;
 width: 100%;
 flex-wrap: nowrap;
 vertical-align: bottom;
`,[n(">",[s("input",[n("&:not(:last-child)",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `),n("&:not(:first-child)",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 margin-left: -1px!important;
 `)]),s("button",[n("&:not(:last-child)",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `,[_("state-border, border",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `)]),n("&:not(:first-child)",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `,[_("state-border, border",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `)])]),n("*",[n("&:not(:last-child)",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `,[n(">",[s("input",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `),s("base-selection",[s("base-selection-label",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `),s("base-selection-tags",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `),_("box-shadow, border, state-border",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `)])])]),n("&:not(:first-child)",`
 margin-left: -1px!important;
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `,[n(">",[s("input",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `),s("base-selection",[s("base-selection-label",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `),s("base-selection-tags",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `),_("box-shadow, border, state-border",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `)])])])])])]),et={},rt=S({name:"InputGroup",props:et,setup(h){const{mergedClsPrefixRef:m}=V(h);return D("-input-group",tt,m),{mergedClsPrefix:m}},render(){const{mergedClsPrefix:h}=this;return R("div",{class:`${h}-input-group`},this.$slots)}}),ot={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},at=S({name:"SearchOutline",render:function(m,c){return d(),L("svg",ot,c[0]||(c[0]=[f("path",{d:"M221.09 64a157.09 157.09 0 1 0 157.09 157.09A157.1 157.1 0 0 0 221.09 64z",fill:"none",stroke:"currentColor","stroke-miterlimit":"10","stroke-width":"32"},null,-1),f("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-miterlimit":"10","stroke-width":"32",d:"M338.29 338.29L448 448"},null,-1)]))}}),st={class:"page-head"},it={class:"page-title"},nt={class:"query-bar"},lt=S({__name:"Database",setup(h){const{fillHeight:m}=X(40),c=i(!1),B=i([]),E=i(null),q=i(""),b=i([]),O=i([]),p=i(""),T=i(0),w=i(""),g=i(""),v=i([]);function M(t){const e=t.indexOf("@@");return e<0?[t,""]:[t.slice(0,e),t.slice(e+2)]}function Q(t){return t==="business"?"璇玑框架库（业务）":t==="log"?"璇玑框架库（日志）":t.startsWith("qqbot:")?`QQ 机器人 · ${t.slice(6)}`:t.startsWith("onebot:")?`OneBot · ${t.slice(7)}`:t}async function K(){try{const t=await k.dbTables(),e=new Map;for(const a of t){const r=a.SOURCE||"business";e.has(r)||e.set(r,[]),e.get(r).push({label:a.TABLE_NAME,value:`${a.TABLE_NAME}@@${r}`})}B.value=[...e.entries()].map(([a,r])=>({type:"group",label:Q(a),key:a,children:r}))}catch(t){p.value=t.message}}async function P(t){if(!t)return;const[e,a]=M(t);q.value=a,c.value=!0,p.value="";try{const r=await k.dbRows(e,a);r.error?(p.value=r.error,b.value=[]):(b.value=r.rows||[],O.value=r.columns||[],T.value=r.count||0)}catch(r){p.value=r.message,b.value=[]}finally{c.value=!1}}async function $(){if(w.value.trim()){g.value="",v.value=[];try{const t=await k.dbQuery(w.value,q.value);t.error?g.value=t.error:v.value=t.rows||[]}catch(t){g.value=t.message}}}return G(K),(t,e)=>(d(),L("div",null,[f("div",st,[f("div",it,[l(o(A),{size:"20",color:"#5b5bd6"},{default:u(()=>[l(o(U))]),_:1}),e[2]||(e[2]=f("span",null,"数据库浏览",-1)),l(o(H),{depth:"3",style:{"font-size":"13px"}},{default:u(()=>[N(C(T.value)+" 行",1)]),_:1})]),l(o(j),{value:E.value,"onUpdate:value":[e[0]||(e[0]=a=>E.value=a),P],options:B.value,placeholder:"选择表",style:{width:"360px"}},null,8,["value","options"])]),p.value?(d(),y(o(I),{key:0,type:"error",title:"查询失败",style:{"margin-bottom":"16px"}},{default:u(()=>[N(C(p.value),1)]),_:1})):x("",!0),b.value.length?(d(),y(z,{key:1,rows:b.value,columns:O.value,"page-size":50,"max-height":o(m),"empty-text":"空表"},null,8,["rows","columns","max-height"])):x("",!0),f("div",nt,[l(o(rt),{style:{width:"600px"}},{default:u(()=>[l(o(Z),{value:w.value,"onUpdate:value":e[1]||(e[1]=a=>w.value=a),placeholder:"只读 SELECT 查询（当前 source）",onKeyup:W($,["enter"])},{prefix:u(()=>[l(o(A),null,{default:u(()=>[l(o(at))]),_:1})]),_:1},8,["value"]),l(o(F),{type:"primary",loading:c.value,onClick:$},{default:u(()=>[...e[3]||(e[3]=[N("执行",-1)])]),_:1},8,["loading"])]),_:1})]),g.value?(d(),y(o(I),{key:2,type:"error",title:"SQL 错误",style:{"margin-top":"12px"}},{default:u(()=>[N(C(g.value),1)]),_:1})):x("",!0),v.value.length?(d(),y(z,{key:3,style:{"margin-top":"12px"},rows:v.value,"page-size":50,"max-height":o(m),"empty-text":"无结果"},null,8,["rows","max-height"])):x("",!0),!b.value.length&&!p.value&&!v.value.length?(d(),y(o(Y),{key:4,description:"从上方选择一个表查看数据",style:{padding:"60px 0"}})):x("",!0)]))}}),_t=J(lt,[["__scopeId","data-v-0278152a"]]);export{_t as default};
