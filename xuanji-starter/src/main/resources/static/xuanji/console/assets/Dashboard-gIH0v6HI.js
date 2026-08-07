import{d as re}from"./dayjs.min-DTiHvpWZ.js";import{c as m,a as C,b as T,d as g,f as k,u as Z,k as ee,h as v,a0 as le,l as ae,p as se,r as K,n as U,o as ce,t as de,a1 as me,q as ue,v as j,x as S,a2 as pe,z as d,A as y,B as he,C as u,a3 as ge,Y as H,a4 as ve,D as s,G as o,H as t,a5 as ye,R as f,T as F,I as V,W as N,Q as x,S as w,K as $,a6 as W,U as fe,F as O,a7 as B,P as q,a8 as xe,a9 as Q,aa as ke,V as be,ab as we,ac as ze,ad as L,_ as _e}from"./index-FgWOnTD7.js";import{P as Ce}from"./PageHero-WvKZEzU4.js";import{_ as Te}from"./CommonChart.vue_vue_type_script_setup_true_lang-CF_VcyE7.js";import{N as I}from"./Empty-D34cG5mJ.js";import{N as Se,a as Ne}from"./Grid-wRV2503Y.js";import{N as E}from"./Tag-BaDApJwM.js";import{N as $e}from"./RadioGroup-BV99d4RK.js";import{N as Oe}from"./NumberAnimation-JDlgC6n9.js";import{N as Be}from"./RadioButton-Cb1DTl0E.js";import{C as je}from"./CubeOutline-DzHXeNrI.js";import"./use-locale-CgRSOBYo.js";import"./get-slot-Bk_rJcZu.js";import"./toNumber-BPZ0FcD_.js";const X=1.25,Pe=m("timeline",`
 position: relative;
 width: 100%;
 display: flex;
 flex-direction: column;
 line-height: ${X};
`,[C("horizontal",`
 flex-direction: row;
 `,[T(">",[m("timeline-item",`
 flex-shrink: 0;
 padding-right: 40px;
 `,[C("dashed-line-type",[T(">",[m("timeline-item-timeline",[g("line",`
 background-image: linear-gradient(90deg, var(--n-color-start), var(--n-color-start) 50%, transparent 50%, transparent 100%);
 background-size: 10px 1px;
 `)])])]),T(">",[m("timeline-item-content",`
 margin-top: calc(var(--n-icon-size) + 12px);
 `,[T(">",[g("meta",`
 margin-top: 6px;
 margin-bottom: unset;
 `)])]),m("timeline-item-timeline",`
 width: 100%;
 height: calc(var(--n-icon-size) + 12px);
 `,[g("line",`
 left: var(--n-icon-size);
 top: calc(var(--n-icon-size) / 2 - 1px);
 right: 0px;
 width: unset;
 height: 2px;
 `)])])])])]),C("right-placement",[m("timeline-item",[m("timeline-item-content",`
 text-align: right;
 margin-right: calc(var(--n-icon-size) + 12px);
 `),m("timeline-item-timeline",`
 width: var(--n-icon-size);
 right: 0;
 `)])]),C("left-placement",[m("timeline-item",[m("timeline-item-content",`
 margin-left: calc(var(--n-icon-size) + 12px);
 `),m("timeline-item-timeline",`
 left: 0;
 `)])]),m("timeline-item",`
 position: relative;
 `,[T("&:last-child",[m("timeline-item-timeline",[g("line",`
 display: none;
 `)]),m("timeline-item-content",[g("meta",`
 margin-bottom: 0;
 `)])]),m("timeline-item-content",[g("title",`
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
 `)]),C("dashed-line-type",[m("timeline-item-timeline",[g("line",`
 --n-color-start: var(--n-line-color);
 transition: --n-color-start .3s var(--n-bezier);
 background-color: transparent;
 background-image: linear-gradient(180deg, var(--n-color-start), var(--n-color-start) 50%, transparent 50%, transparent 100%);
 background-size: 1px 10px;
 `)])]),m("timeline-item-timeline",`
 width: calc(var(--n-icon-size) + 12px);
 position: absolute;
 top: calc(var(--n-title-font-size) * ${X} / 2 - var(--n-icon-size) / 2);
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
 `)])])]),De=Object.assign(Object.assign({},ee.props),{horizontal:Boolean,itemPlacement:{type:String,default:"left"},size:{type:String,default:"medium"},iconSize:Number}),te=ae("n-timeline"),Me=k({name:"Timeline",props:De,setup(i,{slots:e}){const{mergedClsPrefixRef:n}=Z(i),p=ee("Timeline","-timeline",Pe,le,i,n);return se(te,{props:i,mergedThemeRef:p,mergedClsPrefixRef:n}),()=>{const{value:a}=n;return v("div",{class:[`${a}-timeline`,i.horizontal&&`${a}-timeline--horizontal`,`${a}-timeline--${i.size}-size`,!i.horizontal&&`${a}-timeline--${i.itemPlacement}-placement`]},e)}}}),Re={time:[String,Number],title:String,content:String,color:String,lineType:{type:String,default:"default"},type:{type:String,default:"default"}},Ae=k({name:"TimelineItem",props:Re,slots:Object,setup(i){const e=ce(te);e||de("timeline-item","`n-timeline-item` must be placed inside `n-timeline`."),me();const{inlineThemeDisabled:n}=Z(),p=j(()=>{const{props:{size:h,iconSize:b},mergedThemeRef:z}=e,{type:_}=i,{self:{titleTextColor:P,contentTextColor:D,metaTextColor:M,lineColor:r,titleFontWeight:c,contentFontSize:l,[S("iconSize",h)]:R,[S("titleMargin",h)]:A,[S("titleFontSize",h)]:G,[S("circleBorder",_)]:ie,[S("iconColor",_)]:ne},common:{cubicBezierEaseInOut:oe}}=z.value;return{"--n-bezier":oe,"--n-circle-border":ie,"--n-icon-color":ne,"--n-content-font-size":l,"--n-content-text-color":D,"--n-line-color":r,"--n-meta-text-color":M,"--n-title-font-size":G,"--n-title-font-weight":c,"--n-title-margin":A,"--n-title-text-color":P,"--n-icon-size":pe(b)||R}}),a=n?ue("timeline-item",j(()=>{const{props:{size:h,iconSize:b}}=e,{type:z}=i;return`${h[0]}${b||"a"}${z[0]}`}),p,e.props):void 0;return{mergedClsPrefix:e.mergedClsPrefixRef,cssVars:n?void 0:p,themeClass:a==null?void 0:a.themeClass,onRender:a==null?void 0:a.onRender}},render(){const{mergedClsPrefix:i,color:e,onRender:n,$slots:p}=this;return n==null||n(),v("div",{class:[`${i}-timeline-item`,this.themeClass,`${i}-timeline-item--${this.type}-type`,`${i}-timeline-item--${this.lineType}-line-type`],style:this.cssVars},v("div",{class:`${i}-timeline-item-timeline`},v("div",{class:`${i}-timeline-item-timeline__line`}),K(p.icon,a=>a?v("div",{class:`${i}-timeline-item-timeline__icon`,style:{color:e}},a):v("div",{class:`${i}-timeline-item-timeline__circle`,style:{borderColor:e}}))),v("div",{class:`${i}-timeline-item-content`},K(p.header,a=>a||this.title?v("div",{class:`${i}-timeline-item-content__title`},a||this.title):null),v("div",{class:`${i}-timeline-item-content__content`},U(p.default,()=>[this.content])),v("div",{class:`${i}-timeline-item-content__meta`},U(p.footer,()=>[this.time]))))}}),Ge={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Fe=k({name:"AppsOutline",render:function(e,n){return d(),y("svg",Ge,n[0]||(n[0]=[he('<rect x="64" y="64" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="216" y="64" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="368" y="64" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="64" y="216" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="216" y="216" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="368" y="216" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="64" y="368" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="216" y="368" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="368" y="368" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect>',9)]))}}),Ve={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Le=k({name:"BarChartOutline",render:function(e,n){return d(),y("svg",Ve,n[0]||(n[0]=[u("path",{d:"M32 32v432a16 16 0 0 0 16 16h432",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),u("rect",{x:"96",y:"224",width:"80",height:"192",rx:"20",ry:"20",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),u("rect",{x:"240",y:"176",width:"80",height:"240",rx:"20",ry:"20",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),u("rect",{x:"383.64",y:"112",width:"80",height:"304",rx:"20",ry:"20",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1)]))}}),Ie={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Y=k({name:"TrendingDownOutline",render:function(e,n){return d(),y("svg",Ie,n[0]||(n[0]=[u("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M352 368h112V256"},null,-1),u("path",{d:"M48 144l121.37 121.37a32 32 0 0 0 45.26 0l50.74-50.74a32 32 0 0 1 45.26 0L448 352",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1)]))}}),Ee={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},J=k({name:"TrendingUpOutline",render:function(e,n){return d(),y("svg",Ee,n[0]||(n[0]=[u("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M352 144h112v112"},null,-1),u("path",{d:"M48 368l121.37-121.37a32 32 0 0 1 45.26 0l50.74 50.74a32 32 0 0 0 45.26 0L448 160",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1)]))}}),He=ge("dashboard",{state:()=>({days:7,trend:[],loading:!1,err:""}),actions:{async loadTrend(i){this.days=i,this.loading=!0,this.err="";try{const e=await H.getMessageTrend(i);this.trend=Array.isArray(e==null?void 0:e.rows)?e.rows:[]}catch(e){this.err=(e==null?void 0:e.message)||String(e),this.trend=[]}finally{this.loading=!1}}}}),Ke={class:"stat-top"},Ue={class:"trend-title"},We={class:"trend-title"},qe={class:"v-item"},Qe={class:"v-list"},Xe=k({__name:"Dashboard",setup(i){const e=He(),n=N({}),p=N(""),a=N(!1),h=N({current:"",versions:[]}),b={正式版:"success",开发里程碑:"info",规划中:"warning"};async function z(){try{h.value=await H.getVersionLog()}catch{h.value={current:"",versions:[]}}}async function _(){a.value=!0;try{n.value=await H.getDashboard(),p.value=""}catch(r){p.value=r.message}finally{a.value=!1}}const P=j(()=>{const r=n.value;return[{key:"botsOnline",label:"在线机器人",icon:be,color:"#18a058",val:r.botsOnline??0},{key:"botsTotal",label:"机器人总数",icon:Fe,color:"#5b5bd6",val:r.botsTotal??0},{key:"groupsTotal",label:"群聊总数量",icon:we,color:"#2090e0",val:r.groupsTotal??0},{key:"friendsTotal",label:"好友总数量",icon:ze,color:"#f0a020",val:r.friendsTotal??0},{key:"todayGroupAdd",label:"今日加群数量",icon:J,color:"#18a058",val:r.todayGroupAdd??0},{key:"todayGroupDel",label:"今日退群数量",icon:Y,color:"#e5484d",val:r.todayGroupDel??0},{key:"todayFriendAdd",label:"今日加好友数量",icon:J,color:"#18a058",val:r.todayFriendAdd??0},{key:"todayFriendDel",label:"今日删好友数量",icon:Y,color:"#e5484d",val:r.todayFriendDel??0},{key:"todayGroupMessages",label:"今日群聊消息数量",icon:L,color:"#5b5bd6",val:r.todayGroupMessages??0},{key:"todayC2cMessages",label:"今日单聊消息数量",icon:L,color:"#2090e0",val:r.todayC2cMessages??0},{key:"messagesTotal",label:"消息总数量",icon:L,color:"#5b5bd6",val:r.messagesTotal??0},{key:"eventsTotal",label:"系统事件总数量",icon:W,color:"#e58e26",val:r.eventsTotal??0},{key:"pluginsLoaded",label:"已加载插件",icon:je,color:"#5b5bd6",val:r.pluginsLoaded??0}]}),D=[{label:"近 7 天",value:7},{label:"近 15 天",value:15},{label:"近 30 天",value:30}],M=j(()=>{const r=e.trend;return{tooltip:{trigger:"axis"},legend:{data:["单聊消息","群聊消息","总消息"],top:0},grid:{left:52,right:24,top:44,bottom:30},xAxis:{type:"category",boundaryGap:!1,data:r.map(c=>re(c.date).format("MM-DD"))},yAxis:{type:"value"},series:[{name:"单聊消息",type:"line",smooth:!0,symbol:"none",data:r.map(c=>c.c2c),itemStyle:{color:"#5b5bd6"}},{name:"群聊消息",type:"line",smooth:!0,symbol:"none",data:r.map(c=>c.group),itemStyle:{color:"#2090e0"}},{name:"总消息",type:"line",smooth:!0,symbol:"none",data:r.map(c=>c.total),itemStyle:{color:"#18a058"},lineStyle:{width:3},areaStyle:{opacity:.08}}]}});return ve(async()=>{await _(),await e.loadTrend(7),z()}),(r,c)=>(d(),y("div",null,[s(Ce,{title:"璇玑机器人控制台",subtitle:"Xuanji Bot Framework · 实时数据总览",icon:t(ye)},{default:o(()=>[s(t(E),{bordered:!1,type:"success",round:""},{icon:o(()=>[s(t($),null,{default:o(()=>[s(t(W))]),_:1})]),default:o(()=>[x(" "+w((n.value.botsOnline??0)+"/"+(n.value.botsTotal??0))+" 在线 ",1)]),_:1}),s(t(fe),{type:"primary",loading:a.value,onClick:_},{default:o(()=>[...c[1]||(c[1]=[x("刷新数据",-1)])]),_:1},8,["loading"])]),_:1},8,["icon"]),p.value?(d(),f(t(I),{key:0,description:"加载失败："+p.value,style:{padding:"60px 0"}},null,8,["description"])):F("",!0),s(t(Se),{cols:24,"x-gap":12,"y-gap":12,responsive:"screen","item-responsive":"",class:"grid"},{default:o(()=>[(d(!0),y(O,null,B(P.value,l=>(d(),f(t(Ne),{key:l.key,span:"24 s:12 m:8 l:6 xl:4"},{default:o(()=>[s(t(V),{hoverable:"",class:"stat-card","content-style":{padding:"12px 14px"}},{default:o(()=>[u("div",Ke,[u("div",{class:"stat-icon",style:Q({background:l.color+"1a",color:l.color})},[s(t($),{size:"18"},{default:o(()=>[(d(),f(ke(l.icon)))]),_:2},1024)],4),u("div",{class:"stat-value",style:Q({color:l.color})},[s(t(Oe),{from:0,to:Number(l.val)||0,duration:900},null,8,["to"])],4)]),s(t(q),{depth:"3",class:"stat-label"},{default:o(()=>[x(w(l.label),1)]),_:2},1024)]),_:2},1024)]),_:2},1024))),128))]),_:1}),s(t(V),{class:"trend-card",bordered:!0},{header:o(()=>[u("div",Ue,[s(t($),{size:"18",color:"#5b5bd6"},{default:o(()=>[s(t(Le))]),_:1}),c[3]||(c[3]=u("span",null,"消息趋势",-1)),s(t(q),{depth:"3",style:{"font-size":"12px","font-weight":"400"}},{default:o(()=>[...c[2]||(c[2]=[x(" 单聊 / 群聊 / 总消息 · 按天统计 ",-1)])]),_:1})])]),"header-extra":o(()=>[s(t($e),{value:t(e).days,size:"small","onUpdate:value":c[0]||(c[0]=l=>t(e).loadTrend(Number(l)))},{default:o(()=>[(d(),y(O,null,B(D,l=>s(t(Be),{key:l.value,value:l.value},{default:o(()=>[x(w(l.label),1)]),_:2},1032,["value"])),64))]),_:1},8,["value"])]),default:o(()=>[s(Te,{option:M.value,height:"300px"},null,8,["option"]),t(e).err?(d(),f(t(I),{key:0,description:"趋势加载失败："+t(e).err,style:{padding:"40px 0"}},null,8,["description"])):F("",!0)]),_:1}),s(t(V),{class:"version-card",bordered:!0},{header:o(()=>[u("div",We,[s(t($),{size:"18",color:"#854F0B"},{default:o(()=>[s(t(xe))]),_:1}),c[4]||(c[4]=u("span",null,"框架版本日志",-1)),h.value.current?(d(),f(t(E),{key:0,bordered:!1,size:"small",type:"primary",round:""},{default:o(()=>[x(" 当前 "+w(h.value.current),1)]),_:1})):F("",!0)])]),default:o(()=>[h.value.versions.length?(d(),f(t(Me),{key:0,horizontal:"",class:"version-timeline"},{default:o(()=>[(d(!0),y(O,null,B(h.value.versions,(l,R)=>(d(),f(t(Ae),{key:l.version+R,type:b[l.tag]??"default",title:l.version,time:l.date||"——"},{default:o(()=>[u("div",qe,[s(t(E),{bordered:!1,size:"tiny",type:b[l.tag]??"default"},{default:o(()=>[x(w(l.tag),1)]),_:2},1032,["type"]),u("ul",Qe,[(d(!0),y(O,null,B(l.items||[],(A,G)=>(d(),y("li",{key:G},w(A),1))),128))])])]),_:2},1032,["type","title","time"]))),128))]),_:1})):(d(),f(t(I),{key:1,description:"暂无版本日志",style:{padding:"24px 0"}}))]),_:1})]))}}),mt=_e(Xe,[["__scopeId","data-v-4740f7a2"]]);export{mt as default};
