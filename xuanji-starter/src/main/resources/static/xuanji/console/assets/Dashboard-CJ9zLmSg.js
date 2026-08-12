import{d as ne}from"./dayjs.min-DTiHvpWZ.js";import{c as u,a as S,b as $,d as g,f,u as Y,k as Z,h as y,a7 as oe,l as re,p as le,r as K,n as U,o as ae,t as se,a8 as ce,q as de,v as D,x as N,a9 as ue,z as d,A as v,B as me,C as m,aa as pe,a0 as E,ab as he,D as c,G as s,H as o,ac as ge,U as k,W as V,I as W,_ as j,P as C,V as O,J as A,ad as X,X as ve,F as M,Q as P,K as ye,ae as fe,R as ke,af as xe,ag as be,ah as H,ai as we,Y as ze,a1 as Ce}from"./index-B04mKnMx.js";import{P as _e}from"./PageHero-DytW_lJr.js";import{_ as Te}from"./CommonChart.vue_vue_type_script_setup_true_lang-6KJ5Qrzb.js";import{S as Se}from"./StatCard-CpzGkWtf.js";import{N as L,a as I}from"./Tag-CzlZN39e.js";import{N as $e,a as Ne}from"./Grid-CNgMhBhr.js";import{N as Oe}from"./RadioGroup-BednsOXt.js";import{N as Be}from"./RadioButton-D3OAaI6G.js";import{C as je}from"./CubeOutline-1rvEflcm.js";import"./NumberAnimation-gueeV9hb.js";import"./use-locale-C67CdCFa.js";import"./toNumber-Dku0TVsD.js";import"./get-slot-Bk_rJcZu.js";const q=1.25,Me=u("timeline",`
 position: relative;
 width: 100%;
 display: flex;
 flex-direction: column;
 line-height: ${q};
`,[S("horizontal",`
 flex-direction: row;
 `,[$(">",[u("timeline-item",`
 flex-shrink: 0;
 padding-right: 40px;
 `,[S("dashed-line-type",[$(">",[u("timeline-item-timeline",[g("line",`
 background-image: linear-gradient(90deg, var(--n-color-start), var(--n-color-start) 50%, transparent 50%, transparent 100%);
 background-size: 10px 1px;
 `)])])]),$(">",[u("timeline-item-content",`
 margin-top: calc(var(--n-icon-size) + 12px);
 `,[$(">",[g("meta",`
 margin-top: 6px;
 margin-bottom: unset;
 `)])]),u("timeline-item-timeline",`
 width: 100%;
 height: calc(var(--n-icon-size) + 12px);
 `,[g("line",`
 left: var(--n-icon-size);
 top: calc(var(--n-icon-size) / 2 - 1px);
 right: 0px;
 width: unset;
 height: 2px;
 `)])])])])]),S("right-placement",[u("timeline-item",[u("timeline-item-content",`
 text-align: right;
 margin-right: calc(var(--n-icon-size) + 12px);
 `),u("timeline-item-timeline",`
 width: var(--n-icon-size);
 right: 0;
 `)])]),S("left-placement",[u("timeline-item",[u("timeline-item-content",`
 margin-left: calc(var(--n-icon-size) + 12px);
 `),u("timeline-item-timeline",`
 left: 0;
 `)])]),u("timeline-item",`
 position: relative;
 `,[$("&:last-child",[u("timeline-item-timeline",[g("line",`
 display: none;
 `)]),u("timeline-item-content",[g("meta",`
 margin-bottom: 0;
 `)])]),u("timeline-item-content",[g("title",`
 margin: var(--n-title-margin);
 font-size: var(--n-title-font-size);
 transition: color .3s var(--n-bezier);
 font-weight: var(--n-title-font-weight);
 color: var(--n-title-text-color);
 `),g("content",`
 transition: color .3s var(--n-bezier);
 font-size: var(--n-content-font-size);
 color: var(--n-content-text-color);
 `),g("meta",`
 transition: color .3s var(--n-bezier);
 font-size: 12px;
 margin-top: 6px;
 margin-bottom: 20px;
 color: var(--n-meta-text-color);
 `)]),S("dashed-line-type",[u("timeline-item-timeline",[g("line",`
 --n-color-start: var(--n-line-color);
 transition: --n-color-start .3s var(--n-bezier);
 background-color: transparent;
 background-image: linear-gradient(180deg, var(--n-color-start), var(--n-color-start) 50%, transparent 50%, transparent 100%);
 background-size: 1px 10px;
 `)])]),u("timeline-item-timeline",`
 width: calc(var(--n-icon-size) + 12px);
 position: absolute;
 top: calc(var(--n-title-font-size) * ${q} / 2 - var(--n-icon-size) / 2);
 height: 100%;
 `,[g("circle",`
 border: var(--n-circle-border);
 transition:
 background-color .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 width: var(--n-icon-size);
 height: var(--n-icon-size);
 border-radius: var(--n-icon-size);
 box-sizing: border-box;
 `),g("icon",`
 color: var(--n-icon-color);
 font-size: var(--n-icon-size);
 height: var(--n-icon-size);
 width: var(--n-icon-size);
 display: flex;
 align-items: center;
 justify-content: center;
 `),g("line",`
 transition: background-color .3s var(--n-bezier);
 position: absolute;
 top: var(--n-icon-size);
 left: calc(var(--n-icon-size) / 2 - 1px);
 bottom: 0px;
 width: 2px;
 background-color: var(--n-line-color);
 `)])])]),Pe=Object.assign(Object.assign({},Z.props),{horizontal:Boolean,itemPlacement:{type:String,default:"left"},size:{type:String,default:"medium"},iconSize:Number}),ee=re("n-timeline"),De=f({name:"Timeline",props:Pe,setup(t,{slots:e}){const{mergedClsPrefixRef:i}=Y(t),p=Z("Timeline","-timeline",Me,oe,t,i);return le(ee,{props:t,mergedThemeRef:p,mergedClsPrefixRef:i}),()=>{const{value:a}=i;return y("div",{class:[`${a}-timeline`,t.horizontal&&`${a}-timeline--horizontal`,`${a}-timeline--${t.size}-size`,!t.horizontal&&`${a}-timeline--${t.itemPlacement}-placement`]},e)}}}),Re={time:[String,Number],title:String,content:String,color:String,lineType:{type:String,default:"default"},type:{type:String,default:"default"}},Fe=f({name:"TimelineItem",props:Re,slots:Object,setup(t){const e=ae(ee);e||se("timeline-item","`n-timeline-item` must be placed inside `n-timeline`."),ce();const{inlineThemeDisabled:i}=Y(),p=D(()=>{const{props:{size:h,iconSize:x},mergedThemeRef:_}=e,{type:T}=t,{self:{titleTextColor:R,contentTextColor:F,metaTextColor:G,lineColor:n,titleFontWeight:l,contentFontSize:r,[N("iconSize",h)]:b,[N("titleMargin",h)]:w,[N("titleFontSize",h)]:z,[N("circleBorder",T)]:B,[N("iconColor",T)]:te},common:{cubicBezierEaseInOut:ie}}=_.value;return{"--n-bezier":ie,"--n-circle-border":B,"--n-icon-color":te,"--n-content-font-size":r,"--n-content-text-color":F,"--n-line-color":n,"--n-meta-text-color":G,"--n-title-font-size":z,"--n-title-font-weight":l,"--n-title-margin":w,"--n-title-text-color":R,"--n-icon-size":ue(x)||b}}),a=i?de("timeline-item",D(()=>{const{props:{size:h,iconSize:x}}=e,{type:_}=t;return`${h[0]}${x||"a"}${_[0]}`}),p,e.props):void 0;return{mergedClsPrefix:e.mergedClsPrefixRef,cssVars:i?void 0:p,themeClass:a==null?void 0:a.themeClass,onRender:a==null?void 0:a.onRender}},render(){const{mergedClsPrefix:t,color:e,onRender:i,$slots:p}=this;return i==null||i(),y("div",{class:[`${t}-timeline-item`,this.themeClass,`${t}-timeline-item--${this.type}-type`,`${t}-timeline-item--${this.lineType}-line-type`],style:this.cssVars},y("div",{class:`${t}-timeline-item-timeline`},y("div",{class:`${t}-timeline-item-timeline__line`}),K(p.icon,a=>a?y("div",{class:`${t}-timeline-item-timeline__icon`,style:{color:e}},a):y("div",{class:`${t}-timeline-item-timeline__circle`,style:{borderColor:e}}))),y("div",{class:`${t}-timeline-item-content`},K(p.header,a=>a||this.title?y("div",{class:`${t}-timeline-item-content__title`},a||this.title):null),y("div",{class:`${t}-timeline-item-content__content`},U(p.default,()=>[this.content])),y("div",{class:`${t}-timeline-item-content__meta`},U(p.footer,()=>[this.time]))))}}),Ge={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Ve=f({name:"AppsOutline",render:function(e,i){return d(),v("svg",Ge,i[0]||(i[0]=[me('<rect x="64" y="64" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="216" y="64" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="368" y="64" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="64" y="216" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="216" y="216" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="368" y="216" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="64" y="368" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="216" y="368" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="368" y="368" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect>',9)]))}}),Ae={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},He=f({name:"BarChartOutline",render:function(e,i){return d(),v("svg",Ae,i[0]||(i[0]=[m("path",{d:"M32 32v432a16 16 0 0 0 16 16h432",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),m("rect",{x:"96",y:"224",width:"80",height:"192",rx:"20",ry:"20",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),m("rect",{x:"240",y:"176",width:"80",height:"240",rx:"20",ry:"20",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),m("rect",{x:"383.64",y:"112",width:"80",height:"304",rx:"20",ry:"20",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1)]))}}),Le={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Ie=f({name:"HourglassOutline",render:function(e,i){return d(),v("svg",Le,i[0]||(i[0]=[m("path",{d:"M145.61 464h220.78c19.8 0 35.55-16.29 33.42-35.06C386.06 308 304 310 304 256s83.11-51 95.8-172.94c2-18.78-13.61-35.06-33.41-35.06H145.61c-19.8 0-35.37 16.28-33.41 35.06C124.89 205 208 201 208 256s-82.06 52-95.8 172.94c-2.14 18.77 13.61 35.06 33.41 35.06z",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),m("path",{d:"M343.3 432H169.13c-15.6 0-20-18-9.06-29.16C186.55 376 240 356.78 240 326V224c0-19.85-38-35-61.51-67.2c-3.88-5.31-3.49-12.8 6.37-12.8h142.73c8.41 0 10.23 7.43 6.4 12.75C310.82 189 272 204.05 272 224v102c0 30.53 55.71 47 80.4 76.87c9.95 12.04 6.47 29.13-9.1 29.13z",fill:"currentColor"},null,-1)]))}}),Ee={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},J=f({name:"TrendingDownOutline",render:function(e,i){return d(),v("svg",Ee,i[0]||(i[0]=[m("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M352 368h112V256"},null,-1),m("path",{d:"M48 144l121.37 121.37a32 32 0 0 0 45.26 0l50.74-50.74a32 32 0 0 1 45.26 0L448 352",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1)]))}}),Ke={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Q=f({name:"TrendingUpOutline",render:function(e,i){return d(),v("svg",Ke,i[0]||(i[0]=[m("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M352 144h112v112"},null,-1),m("path",{d:"M48 368l121.37-121.37a32 32 0 0 1 45.26 0l50.74 50.74a32 32 0 0 0 45.26 0L448 160",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1)]))}}),Ue=pe("dashboard",{state:()=>({days:7,trend:[],loading:!1,err:""}),actions:{async loadTrend(t){this.days=t,this.loading=!0,this.err="";try{const e=await E.getMessageTrend(t);this.trend=Array.isArray(e==null?void 0:e.rows)?e.rows:[]}catch(e){this.err=(e==null?void 0:e.message)||String(e),this.trend=[]}finally{this.loading=!1}}}}),We={class:"trend-title"},Xe={class:"trend-title"},qe={class:"v-item"},Je={class:"v-list"},Qe=f({__name:"Dashboard",setup(t){const e=Ue(),i=j({}),p=j(""),a=j(!1),h=j({current:"",versions:[]}),x={正式版:"success",开发里程碑:"info",规划中:"warning"};async function _(){try{h.value=await E.getVersionLog()}catch{h.value={current:"",versions:[]}}}async function T(){a.value=!0;try{i.value=await E.getDashboard(),p.value=""}catch(n){p.value=n.message}finally{a.value=!1}}const R=D(()=>{var w,z,B;const n=i.value,l=Number(n.commandCount??0),r=Number(((w=n.dedup)==null?void 0:w.dbDedupSuccess)??0)+Number(((z=n.dedup)==null?void 0:z.localFallbackCount)??0),b=Number(((B=n.plugins)==null?void 0:B.pluginTimeoutCount)??0);return[{key:"botsOnline",label:"在线机器人",icon:ke,color:"#18a058",val:n.botsOnline??0},{key:"botsTotal",label:"机器人总数",icon:Ve,color:"#5b5bd6",val:n.botsTotal??0},{key:"groupsTotal",label:"群聊总数量",icon:xe,color:"#2090e0",val:n.groupsTotal??0},{key:"friendsTotal",label:"好友总数量",icon:be,color:"#f0a020",val:n.friendsTotal??0},{key:"todayGroupAdd",label:"今日加群数量",icon:Q,color:"#18a058",val:n.todayGroupAdd??0},{key:"todayGroupDel",label:"今日退群数量",icon:J,color:"#e5484d",val:n.todayGroupDel??0},{key:"todayFriendAdd",label:"今日加好友数量",icon:Q,color:"#18a058",val:n.todayFriendAdd??0},{key:"todayFriendDel",label:"今日删好友数量",icon:J,color:"#e5484d",val:n.todayFriendDel??0},{key:"todayGroupMessages",label:"今日群聊消息数量",icon:H,color:"#5b5bd6",val:n.todayGroupMessages??0},{key:"todayC2cMessages",label:"今日单聊消息数量",icon:H,color:"#2090e0",val:n.todayC2cMessages??0},{key:"messagesTotal",label:"消息总数量",icon:H,color:"#5b5bd6",val:n.messagesTotal??0},{key:"eventsTotal",label:"系统事件总数量",icon:X,color:"#e58e26",val:n.eventsTotal??0},{key:"pluginsLoaded",label:"已加载插件",icon:je,color:"#5b5bd6",val:n.pluginsLoaded??0},{key:"cmdHandlers",label:"插件命令数",icon:we,color:"#5b5bd6",val:l},{key:"pluginTimeout",label:"插件超时次数",icon:Ie,color:b>0?"#e5484d":"#5b5bd6",val:b},{key:"dedupTotal",label:"消息去重次数",icon:ze,color:"#18a058",val:r}]}),F=[{label:"近 7 天",value:7},{label:"近 15 天",value:15},{label:"近 30 天",value:30}],G=D(()=>{const n=e.trend;return{tooltip:{trigger:"axis"},legend:{data:["单聊消息","群聊消息","总消息"],top:0},grid:{left:52,right:24,top:44,bottom:30},xAxis:{type:"category",boundaryGap:!1,data:n.map(l=>ne(l.date).format("MM-DD"))},yAxis:{type:"value"},series:[{name:"单聊消息",type:"line",smooth:!0,symbol:"none",data:n.map(l=>l.c2c),itemStyle:{color:"#5b5bd6"}},{name:"群聊消息",type:"line",smooth:!0,symbol:"none",data:n.map(l=>l.group),itemStyle:{color:"#2090e0"}},{name:"总消息",type:"line",smooth:!0,symbol:"none",data:n.map(l=>l.total),itemStyle:{color:"#18a058"},lineStyle:{width:3},areaStyle:{opacity:.08}}]}});return he(async()=>{await T(),await e.loadTrend(7),_()}),(n,l)=>(d(),v("div",null,[c(_e,{title:"璇玑机器人控制台",subtitle:"Xuanji Bot Framework · 实时数据总览",icon:o(ge)},{default:s(()=>[c(o(I),{bordered:!1,type:"success",round:""},{icon:s(()=>[c(o(A),null,{default:s(()=>[c(o(X))]),_:1})]),default:s(()=>[C(" "+O((i.value.botsOnline??0)+"/"+(i.value.botsTotal??0))+" 在线 ",1)]),_:1}),c(o(ve),{type:"primary",loading:a.value,onClick:T},{default:s(()=>[...l[1]||(l[1]=[C("刷新数据",-1)])]),_:1},8,["loading"])]),_:1},8,["icon"]),p.value?(d(),k(o(L),{key:0,description:"加载失败："+p.value,style:{padding:"60px 0"}},null,8,["description"])):V("",!0),c(o($e),{cols:24,"x-gap":12,"y-gap":12,responsive:"screen","item-responsive":"",class:"grid"},{default:s(()=>[(d(!0),v(M,null,P(R.value,r=>(d(),k(o(Ne),{key:r.key,span:"24 s:12 m:8 l:6 xl:4"},{default:s(()=>[c(Se,{icon:r.icon,color:r.color,value:r.val,label:r.label},null,8,["icon","color","value","label"])]),_:2},1024))),128))]),_:1}),c(o(W),{class:"trend-card",bordered:!0},{header:s(()=>[m("div",We,[c(o(A),{size:"18",color:"#5b5bd6"},{default:s(()=>[c(o(He))]),_:1}),l[3]||(l[3]=m("span",null,"消息趋势",-1)),c(o(ye),{depth:"3",style:{"font-size":"12px","font-weight":"400"}},{default:s(()=>[...l[2]||(l[2]=[C(" 单聊 / 群聊 / 总消息 · 按天统计 ",-1)])]),_:1})])]),"header-extra":s(()=>[c(o(Oe),{value:o(e).days,size:"small","onUpdate:value":l[0]||(l[0]=r=>o(e).loadTrend(Number(r)))},{default:s(()=>[(d(),v(M,null,P(F,r=>c(o(Be),{key:r.value,value:r.value},{default:s(()=>[C(O(r.label),1)]),_:2},1032,["value"])),64))]),_:1},8,["value"])]),default:s(()=>[c(Te,{option:G.value,height:"300px"},null,8,["option"]),o(e).err?(d(),k(o(L),{key:0,description:"趋势加载失败："+o(e).err,style:{padding:"40px 0"}},null,8,["description"])):V("",!0)]),_:1}),c(o(W),{class:"version-card",bordered:!0},{header:s(()=>[m("div",Xe,[c(o(A),{size:"18",color:"#854F0B"},{default:s(()=>[c(o(fe))]),_:1}),l[4]||(l[4]=m("span",null,"框架版本日志",-1)),h.value.current?(d(),k(o(I),{key:0,bordered:!1,size:"small",type:"primary",round:""},{default:s(()=>[C(" 当前 "+O(h.value.current),1)]),_:1})):V("",!0)])]),default:s(()=>[h.value.versions.length?(d(),k(o(De),{key:0,horizontal:"",class:"version-timeline"},{default:s(()=>[(d(!0),v(M,null,P(h.value.versions,(r,b)=>(d(),k(o(Fe),{key:r.version+b,type:x[r.tag]??"default",title:r.version,time:r.date||"——"},{default:s(()=>[m("div",qe,[c(o(I),{bordered:!1,size:"tiny",type:x[r.tag]??"default"},{default:s(()=>[C(O(r.tag),1)]),_:2},1032,["type"]),m("ul",Je,[(d(!0),v(M,null,P(r.items||[],(w,z)=>(d(),v("li",{key:z},O(w),1))),128))])])]),_:2},1032,["type","title","time"]))),128))]),_:1})):(d(),k(o(L),{key:1,description:"暂无版本日志",style:{padding:"24px 0"}}))]),_:1})]))}}),mt=Ce(Qe,[["__scopeId","data-v-66289393"]]);export{mt as default};
