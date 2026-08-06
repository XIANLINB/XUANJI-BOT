import{f as S,h as r,m as ee,v as C,Y as re,Z as te,$ as oe,a0 as ie,a1 as ne,a2 as D,b as j,c as h,a as M,u as se,k as le,q as ae,a3 as ce,x as X,z as $,A as O,C as de,B as k,a4 as ue,D as g,G as v,H as p,a5 as ge,Q as V,S as pe,I as H,V as W,X as G,P as N,R as T,K as I,a6 as Y,T as fe,F as he,a7 as me,O as F,a8 as ye,a9 as E,aa as ve,U as be,ab as xe,ac as ke,ad as U,_ as we}from"./index-CniGiLyP.js";import{P as Ce}from"./PageHero-CYI97ukc.js";import{N as $e}from"./Empty-kAFF6Qt4.js";import{N as K,a as A,S as Se,H as Pe,C as _e}from"./SpeedometerOutline-DxPG6V_r.js";import{N as ze}from"./Tag-BCQAS3gm.js";import"./use-locale-Bn7lCNC0.js";import"./get-slot-Bk_rJcZu.js";const Be={success:r(ie,null),error:r(oe,null),warning:r(te,null),info:r(re,null)},Me=S({name:"ProgressCircle",props:{clsPrefix:{type:String,required:!0},status:{type:String,required:!0},strokeWidth:{type:Number,required:!0},fillColor:[String,Object],railColor:String,railStyle:[String,Object],percentage:{type:Number,default:0},offsetDegree:{type:Number,default:0},showIndicator:{type:Boolean,required:!0},indicatorTextColor:String,unit:String,viewBoxWidth:{type:Number,required:!0},gapDegree:{type:Number,required:!0},gapOffsetDegree:{type:Number,default:0}},setup(e,{slots:d}){const s=C(()=>{const i="gradient",{fillColor:o}=e;return typeof o=="object"?`${i}-${ne(JSON.stringify(o))}`:i});function b(i,o,c,m){const{gapDegree:x,viewBoxWidth:t,strokeWidth:l}=e,n=50,y=0,f=n,a=0,P=2*n,_=50+l/2,w=`M ${_},${_} m ${y},${f}
      a ${n},${n} 0 1 1 ${a},${-P}
      a ${n},${n} 0 1 1 ${-a},${P}`,z=Math.PI*2*n,B={stroke:m==="rail"?c:typeof e.fillColor=="object"?`url(#${s.value})`:c,strokeDasharray:`${Math.min(i,100)/100*(z-x)}px ${t*8}px`,strokeDashoffset:`-${x/2}px`,transformOrigin:o?"center":void 0,transform:o?`rotate(${o}deg)`:void 0};return{pathString:w,pathStyle:B}}const u=()=>{const i=typeof e.fillColor=="object",o=i?e.fillColor.stops[0]:"",c=i?e.fillColor.stops[1]:"";return i&&r("defs",null,r("linearGradient",{id:s.value,x1:"0%",y1:"100%",x2:"100%",y2:"0%"},r("stop",{offset:"0%","stop-color":o}),r("stop",{offset:"100%","stop-color":c})))};return()=>{const{fillColor:i,railColor:o,strokeWidth:c,offsetDegree:m,status:x,percentage:t,showIndicator:l,indicatorTextColor:n,unit:y,gapOffsetDegree:f,clsPrefix:a}=e,{pathString:P,pathStyle:_}=b(100,0,o,"rail"),{pathString:w,pathStyle:z}=b(t,m,i,"fill"),B=100+c;return r("div",{class:`${a}-progress-content`,role:"none"},r("div",{class:`${a}-progress-graph`,"aria-hidden":!0},r("div",{class:`${a}-progress-graph-circle`,style:{transform:f?`rotate(${f}deg)`:void 0}},r("svg",{viewBox:`0 0 ${B} ${B}`},u(),r("g",null,r("path",{class:`${a}-progress-graph-circle-rail`,d:P,"stroke-width":c,"stroke-linecap":"round",fill:"none",style:_})),r("g",null,r("path",{class:[`${a}-progress-graph-circle-fill`,t===0&&`${a}-progress-graph-circle-fill--empty`],d:w,"stroke-width":c,"stroke-linecap":"round",fill:"none",style:z}))))),l?r("div",null,d.default?r("div",{class:`${a}-progress-custom-content`,role:"none"},d.default()):x!=="default"?r("div",{class:`${a}-progress-icon`,"aria-hidden":!0},r(ee,{clsPrefix:a},{default:()=>Be[x]})):r("div",{class:`${a}-progress-text`,style:{color:n},role:"none"},r("span",{class:`${a}-progress-text__percentage`},t),r("span",{class:`${a}-progress-text__unit`},y))):null)}}}),Ne={success:r(ie,null),error:r(oe,null),warning:r(te,null),info:r(re,null)},De=S({name:"ProgressLine",props:{clsPrefix:{type:String,required:!0},percentage:{type:Number,default:0},railColor:String,railStyle:[String,Object],fillColor:[String,Object],status:{type:String,required:!0},indicatorPlacement:{type:String,required:!0},indicatorTextColor:String,unit:{type:String,default:"%"},processing:{type:Boolean,required:!0},showIndicator:{type:Boolean,required:!0},height:[String,Number],railBorderRadius:[String,Number],fillBorderRadius:[String,Number]},setup(e,{slots:d}){const s=C(()=>D(e.height)),b=C(()=>{var o,c;return typeof e.fillColor=="object"?`linear-gradient(to right, ${(o=e.fillColor)===null||o===void 0?void 0:o.stops[0]} , ${(c=e.fillColor)===null||c===void 0?void 0:c.stops[1]})`:e.fillColor}),u=C(()=>e.railBorderRadius!==void 0?D(e.railBorderRadius):e.height!==void 0?D(e.height,{c:.5}):""),i=C(()=>e.fillBorderRadius!==void 0?D(e.fillBorderRadius):e.railBorderRadius!==void 0?D(e.railBorderRadius):e.height!==void 0?D(e.height,{c:.5}):"");return()=>{const{indicatorPlacement:o,railColor:c,railStyle:m,percentage:x,unit:t,indicatorTextColor:l,status:n,showIndicator:y,processing:f,clsPrefix:a}=e;return r("div",{class:`${a}-progress-content`,role:"none"},r("div",{class:`${a}-progress-graph`,"aria-hidden":!0},r("div",{class:[`${a}-progress-graph-line`,{[`${a}-progress-graph-line--indicator-${o}`]:!0}]},r("div",{class:`${a}-progress-graph-line-rail`,style:[{backgroundColor:c,height:s.value,borderRadius:u.value},m]},r("div",{class:[`${a}-progress-graph-line-fill`,f&&`${a}-progress-graph-line-fill--processing`],style:{maxWidth:`${e.percentage}%`,background:b.value,height:s.value,lineHeight:s.value,borderRadius:i.value}},o==="inside"?r("div",{class:`${a}-progress-graph-line-indicator`,style:{color:l}},d.default?d.default():`${x}${t}`):null)))),y&&o==="outside"?r("div",null,d.default?r("div",{class:`${a}-progress-custom-content`,style:{color:l},role:"none"},d.default()):n==="default"?r("div",{role:"none",class:`${a}-progress-icon ${a}-progress-icon--as-text`,style:{color:l}},x,t):r("div",{class:`${a}-progress-icon`,"aria-hidden":!0},r(ee,{clsPrefix:a},{default:()=>Ne[n]}))):null)}}});function Z(e,d,s=100){return`m ${s/2} ${s/2-e} a ${e} ${e} 0 1 1 0 ${2*e} a ${e} ${e} 0 1 1 0 -${2*e}`}const Oe=S({name:"ProgressMultipleCircle",props:{clsPrefix:{type:String,required:!0},viewBoxWidth:{type:Number,required:!0},percentage:{type:Array,default:[0]},strokeWidth:{type:Number,required:!0},circleGap:{type:Number,required:!0},showIndicator:{type:Boolean,required:!0},fillColor:{type:Array,default:()=>[]},railColor:{type:Array,default:()=>[]},railStyle:{type:Array,default:()=>[]}},setup(e,{slots:d}){const s=C(()=>e.percentage.map((i,o)=>`${Math.PI*i/100*(e.viewBoxWidth/2-e.strokeWidth/2*(1+2*o)-e.circleGap*o)*2}, ${e.viewBoxWidth*8}`)),b=(u,i)=>{const o=e.fillColor[i],c=typeof o=="object"?o.stops[0]:"",m=typeof o=="object"?o.stops[1]:"";return typeof e.fillColor[i]=="object"&&r("linearGradient",{id:`gradient-${i}`,x1:"100%",y1:"0%",x2:"0%",y2:"100%"},r("stop",{offset:"0%","stop-color":c}),r("stop",{offset:"100%","stop-color":m}))};return()=>{const{viewBoxWidth:u,strokeWidth:i,circleGap:o,showIndicator:c,fillColor:m,railColor:x,railStyle:t,percentage:l,clsPrefix:n}=e;return r("div",{class:`${n}-progress-content`,role:"none"},r("div",{class:`${n}-progress-graph`,"aria-hidden":!0},r("div",{class:`${n}-progress-graph-circle`},r("svg",{viewBox:`0 0 ${u} ${u}`},r("defs",null,l.map((y,f)=>b(y,f))),l.map((y,f)=>r("g",{key:f},r("path",{class:`${n}-progress-graph-circle-rail`,d:Z(u/2-i/2*(1+2*f)-o*f,i,u),"stroke-width":i,"stroke-linecap":"round",fill:"none",style:[{strokeDashoffset:0,stroke:x[f]},t[f]]}),r("path",{class:[`${n}-progress-graph-circle-fill`,y===0&&`${n}-progress-graph-circle-fill--empty`],d:Z(u/2-i/2*(1+2*f)-o*f,i,u),"stroke-width":i,"stroke-linecap":"round",fill:"none",style:{strokeDasharray:s.value[f],strokeDashoffset:0,stroke:typeof m[f]=="object"?`url(#gradient-${f})`:m[f]}})))))),c&&d.default?r("div",null,r("div",{class:`${n}-progress-text`},d.default())):null)}}}),Re=j([h("progress",{display:"inline-block"},[h("progress-icon",`
 color: var(--n-icon-color);
 transition: color .3s var(--n-bezier);
 `),M("line",`
 width: 100%;
 display: block;
 `,[h("progress-content",`
 display: flex;
 align-items: center;
 `,[h("progress-graph",{flex:1})]),h("progress-custom-content",{marginLeft:"14px"}),h("progress-icon",`
 width: 30px;
 padding-left: 14px;
 height: var(--n-icon-size-line);
 line-height: var(--n-icon-size-line);
 font-size: var(--n-icon-size-line);
 `,[M("as-text",`
 color: var(--n-text-color-line-outer);
 text-align: center;
 width: 40px;
 font-size: var(--n-font-size);
 padding-left: 4px;
 transition: color .3s var(--n-bezier);
 `)])]),M("circle, dashboard",{width:"120px"},[h("progress-custom-content",`
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 justify-content: center;
 `),h("progress-text",`
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 color: inherit;
 font-size: var(--n-font-size-circle);
 color: var(--n-text-color-circle);
 font-weight: var(--n-font-weight-circle);
 transition: color .3s var(--n-bezier);
 white-space: nowrap;
 `),h("progress-icon",`
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 color: var(--n-icon-color);
 font-size: var(--n-icon-size-circle);
 `)]),M("multiple-circle",`
 width: 200px;
 color: inherit;
 `,[h("progress-text",`
 font-weight: var(--n-font-weight-circle);
 color: var(--n-text-color-circle);
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 justify-content: center;
 transition: color .3s var(--n-bezier);
 `)]),h("progress-content",{position:"relative"}),h("progress-graph",{position:"relative"},[h("progress-graph-circle",[j("svg",{verticalAlign:"bottom"}),h("progress-graph-circle-fill",`
 stroke: var(--n-fill-color);
 transition:
 opacity .3s var(--n-bezier),
 stroke .3s var(--n-bezier),
 stroke-dasharray .3s var(--n-bezier);
 `,[M("empty",{opacity:0})]),h("progress-graph-circle-rail",`
 transition: stroke .3s var(--n-bezier);
 overflow: hidden;
 stroke: var(--n-rail-color);
 `)]),h("progress-graph-line",[M("indicator-inside",[h("progress-graph-line-rail",`
 height: 16px;
 line-height: 16px;
 border-radius: 10px;
 `,[h("progress-graph-line-fill",`
 height: inherit;
 border-radius: 10px;
 `),h("progress-graph-line-indicator",`
 background: #0000;
 white-space: nowrap;
 text-align: right;
 margin-left: 14px;
 margin-right: 14px;
 height: inherit;
 font-size: 12px;
 color: var(--n-text-color-line-inner);
 transition: color .3s var(--n-bezier);
 `)])]),M("indicator-inside-label",`
 height: 16px;
 display: flex;
 align-items: center;
 `,[h("progress-graph-line-rail",`
 flex: 1;
 transition: background-color .3s var(--n-bezier);
 `),h("progress-graph-line-indicator",`
 background: var(--n-fill-color);
 font-size: 12px;
 transform: translateZ(0);
 display: flex;
 vertical-align: middle;
 height: 16px;
 line-height: 16px;
 padding: 0 10px;
 border-radius: 10px;
 position: absolute;
 white-space: nowrap;
 color: var(--n-text-color-line-inner);
 transition:
 right .2s var(--n-bezier),
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 `)]),h("progress-graph-line-rail",`
 position: relative;
 overflow: hidden;
 height: var(--n-rail-height);
 border-radius: 5px;
 background-color: var(--n-rail-color);
 transition: background-color .3s var(--n-bezier);
 `,[h("progress-graph-line-fill",`
 background: var(--n-fill-color);
 position: relative;
 border-radius: 5px;
 height: inherit;
 width: 100%;
 max-width: 0%;
 transition:
 background-color .3s var(--n-bezier),
 max-width .2s var(--n-bezier);
 `,[M("processing",[j("&::after",`
 content: "";
 background-image: var(--n-line-bg-processing);
 animation: progress-processing-animation 2s var(--n-bezier) infinite;
 `)])])])])])]),j("@keyframes progress-processing-animation",`
 0% {
 position: absolute;
 left: 0;
 top: 0;
 bottom: 0;
 right: 100%;
 opacity: 1;
 }
 66% {
 position: absolute;
 left: 0;
 top: 0;
 bottom: 0;
 right: 0;
 opacity: 0;
 }
 100% {
 position: absolute;
 left: 0;
 top: 0;
 bottom: 0;
 right: 0;
 opacity: 0;
 }
 `)]),Te=Object.assign(Object.assign({},le.props),{processing:Boolean,type:{type:String,default:"line"},gapDegree:Number,gapOffsetDegree:Number,status:{type:String,default:"default"},railColor:[String,Array],railStyle:[String,Array],color:[String,Array,Object],viewBoxWidth:{type:Number,default:100},strokeWidth:{type:Number,default:7},percentage:[Number,Array],unit:{type:String,default:"%"},showIndicator:{type:Boolean,default:!0},indicatorPosition:{type:String,default:"outside"},indicatorPlacement:{type:String,default:"outside"},indicatorTextColor:String,circleGap:{type:Number,default:1},height:Number,borderRadius:[String,Number],fillBorderRadius:[String,Number],offsetDegree:Number}),Ie=S({name:"Progress",props:Te,setup(e){const d=C(()=>e.indicatorPlacement||e.indicatorPosition),s=C(()=>{if(e.gapDegree||e.gapDegree===0)return e.gapDegree;if(e.type==="dashboard")return 75}),{mergedClsPrefixRef:b,inlineThemeDisabled:u}=se(e),i=le("Progress","-progress",Re,ce,e,b),o=C(()=>{const{status:m}=e,{common:{cubicBezierEaseInOut:x},self:{fontSize:t,fontSizeCircle:l,railColor:n,railHeight:y,iconSizeCircle:f,iconSizeLine:a,textColorCircle:P,textColorLineInner:_,textColorLineOuter:w,lineBgProcessing:z,fontWeightCircle:B,[X("iconColor",m)]:q,[X("fillColor",m)]:R}}=i.value;return{"--n-bezier":x,"--n-fill-color":R,"--n-font-size":t,"--n-font-size-circle":l,"--n-font-weight-circle":B,"--n-icon-color":q,"--n-icon-size-circle":f,"--n-icon-size-line":a,"--n-line-bg-processing":z,"--n-rail-color":n,"--n-rail-height":y,"--n-text-color-circle":P,"--n-text-color-line-inner":_,"--n-text-color-line-outer":w}}),c=u?ae("progress",C(()=>e.status[0]),o,e):void 0;return{mergedClsPrefix:b,mergedIndicatorPlacement:d,gapDeg:s,cssVars:u?void 0:o,themeClass:c==null?void 0:c.themeClass,onRender:c==null?void 0:c.onRender}},render(){const{type:e,cssVars:d,indicatorTextColor:s,showIndicator:b,status:u,railColor:i,railStyle:o,color:c,percentage:m,viewBoxWidth:x,strokeWidth:t,mergedIndicatorPlacement:l,unit:n,borderRadius:y,fillBorderRadius:f,height:a,processing:P,circleGap:_,mergedClsPrefix:w,gapDeg:z,gapOffsetDegree:B,themeClass:q,$slots:R,onRender:L}=this;return L==null||L(),r("div",{class:[q,`${w}-progress`,`${w}-progress--${e}`,`${w}-progress--${u}`],style:d,"aria-valuemax":100,"aria-valuemin":0,"aria-valuenow":m,role:e==="circle"||e==="line"||e==="dashboard"?"progressbar":"none"},e==="circle"||e==="dashboard"?r(Me,{clsPrefix:w,status:u,showIndicator:b,indicatorTextColor:s,railColor:i,fillColor:c,railStyle:o,offsetDegree:this.offsetDegree,percentage:m,viewBoxWidth:x,strokeWidth:t,gapDegree:z===void 0?e==="dashboard"?75:0:z,gapOffsetDegree:B,unit:n},R):e==="line"?r(De,{clsPrefix:w,status:u,showIndicator:b,indicatorTextColor:s,railColor:i,fillColor:c,railStyle:o,percentage:m,processing:P,indicatorPlacement:l,unit:n,fillBorderRadius:f,railBorderRadius:y,height:a},R):e==="multiple-circle"?r(Oe,{clsPrefix:w,strokeWidth:t,railColor:i,fillColor:c,railStyle:o,viewBoxWidth:x,percentage:m,showIndicator:b,circleGap:_},R):null)}}),je={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},We=S({name:"AppsOutline",render:function(d,s){return $(),O("svg",je,s[0]||(s[0]=[de('<rect x="64" y="64" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="216" y="64" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="368" y="64" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="64" y="216" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="216" y="216" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="368" y="216" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="64" y="368" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="216" y="368" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect><rect x="368" y="368" width="80" height="80" rx="40" ry="40" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32"></rect>',9)]))}}),Ge={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Ae=S({name:"TimeOutline",render:function(d,s){return $(),O("svg",Ge,s[0]||(s[0]=[k("path",{d:"M256 64C150 64 64 150 64 256s86 192 192 192s192-86 192-192S362 64 256 64z",fill:"none",stroke:"currentColor","stroke-miterlimit":"10","stroke-width":"32"},null,-1),k("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M256 128v144h96"},null,-1)]))}}),qe={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},J=S({name:"TrendingDownOutline",render:function(d,s){return $(),O("svg",qe,s[0]||(s[0]=[k("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M352 368h112V256"},null,-1),k("path",{d:"M48 144l121.37 121.37a32 32 0 0 0 45.26 0l50.74-50.74a32 32 0 0 1 45.26 0L448 352",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1)]))}}),Le={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Q=S({name:"TrendingUpOutline",render:function(d,s){return $(),O("svg",Le,s[0]||(s[0]=[k("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M352 144h112v112"},null,-1),k("path",{d:"M48 368l121.37-121.37a32 32 0 0 1 45.26 0l50.74 50.74a32 32 0 0 0 45.26 0L448 160",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1)]))}}),Ve={class:"stat-top"},Fe={class:"sys-item"},Ue={class:"sys-label"},Xe={class:"sys-item"},He={class:"sys-label"},Ye={class:"sys-item"},Ee={class:"sys-label"},Ke=S({__name:"Dashboard",setup(e){const d=W({}),s=W(""),b=W(!1),u=W({heapUsedMb:null,heapMaxMb:null,uptimeMin:null});async function i(){b.value=!0;try{d.value=await G.getDashboard(),s.value=""}catch(t){s.value=t.message}finally{b.value=!1}}async function o(){const[t,l,n]=await Promise.all([G.getActuatorMetric("jvm.memory.used","area:heap"),G.getActuatorMetric("jvm.memory.max","area:heap"),G.getActuatorMetric("process.uptime")]);u.value={heapUsedMb:t!=null?Math.round(t/1048576):null,heapMaxMb:l!=null?Math.round(l/1048576):null,uptimeMin:n!=null?Math.round(n/60):null}}ue(async()=>{await i(),o()});const c=C(()=>{const t=d.value;return[{key:"botsOnline",label:"在线机器人",icon:be,color:"#18a058",val:t.botsOnline??0},{key:"botsTotal",label:"机器人总数",icon:We,color:"#5b5bd6",val:t.botsTotal??0},{key:"groupsTotal",label:"群聊总数量",icon:xe,color:"#2090e0",val:t.groupsTotal??0},{key:"friendsTotal",label:"好友总数量",icon:ke,color:"#f0a020",val:t.friendsTotal??0},{key:"todayGroupAdd",label:"今日加群数量",icon:Q,color:"#18a058",val:t.todayGroupAdd??0},{key:"todayGroupDel",label:"今日退群数量",icon:J,color:"#e5484d",val:t.todayGroupDel??0},{key:"todayFriendAdd",label:"今日加好友数量",icon:Q,color:"#18a058",val:t.todayFriendAdd??0},{key:"todayFriendDel",label:"今日删好友数量",icon:J,color:"#e5484d",val:t.todayFriendDel??0},{key:"todayGroupMessages",label:"今日群聊消息数量",icon:U,color:"#5b5bd6",val:t.todayGroupMessages??0},{key:"todayC2cMessages",label:"今日单聊消息数量",icon:U,color:"#2090e0",val:t.todayC2cMessages??0},{key:"messagesTotal",label:"消息总数量",icon:U,color:"#5b5bd6",val:t.messagesTotal??0},{key:"eventsTotal",label:"系统事件总数量",icon:Y,color:"#e58e26",val:t.eventsTotal??0},{key:"pluginsLoaded",label:"已加载插件",icon:_e,color:"#5b5bd6",val:t.pluginsLoaded??0}]}),m=C(()=>{const{heapUsedMb:t,heapMaxMb:l}=u.value;return!t||!l?0:Math.min(100,Math.round(t/l*100))});function x(t){if(t==null)return"—";const l=Math.floor(t/60),n=t%60;return l>0?`${l} 小时 ${n} 分`:`${n} 分`}return(t,l)=>{const n=ye("NStatistic");return $(),O("div",null,[g(Ce,{title:"璇玑机器人控制台",subtitle:"Xuanji Bot Framework · 实时数据总览",icon:p(ge)},{default:v(()=>[g(p(ze),{bordered:!1,type:"success",round:""},{icon:v(()=>[g(p(I),null,{default:v(()=>[g(p(Y))]),_:1})]),default:v(()=>[N(" "+T((d.value.botsOnline??0)+"/"+(d.value.botsTotal??0))+" 在线 ",1)]),_:1}),g(p(fe),{type:"primary",loading:b.value,onClick:i},{default:v(()=>[...l[0]||(l[0]=[N("刷新数据",-1)])]),_:1},8,["loading"])]),_:1},8,["icon"]),s.value?($(),V(p($e),{key:0,description:"加载失败："+s.value,style:{padding:"60px 0"}},null,8,["description"])):pe("",!0),g(p(K),{cols:24,"x-gap":12,"y-gap":12,responsive:"screen","item-responsive":"",class:"grid"},{default:v(()=>[($(!0),O(he,null,me(c.value,y=>($(),V(p(A),{key:y.key,span:"24 s:12 m:8 l:6 xl:4"},{default:v(()=>[g(p(H),{hoverable:"",class:"stat-card","content-style":{padding:"12px 14px"}},{default:v(()=>[k("div",Ve,[k("div",{class:"stat-icon",style:E({background:y.color+"1a",color:y.color})},[g(p(I),{size:"18"},{default:v(()=>[($(),V(ve(y.icon)))]),_:2},1024)],4),k("div",{class:"stat-value",style:E({color:y.color})},T(y.val),5)]),g(p(F),{depth:"3",class:"stat-label"},{default:v(()=>[N(T(y.label),1)]),_:2},1024)]),_:2},1024)]),_:2},1024))),128))]),_:1}),g(p(H),{title:"运行信息",class:"sys-card"},{"header-extra":v(()=>[g(p(F),{depth:"3",style:{"font-size":"12px"}},{default:v(()=>[...l[1]||(l[1]=[N("数据来自 /actuator/metrics",-1)])]),_:1})]),default:v(()=>[g(p(K),{cols:3,"x-gap":20,responsive:"screen","item-responsive":""},{default:v(()=>[g(p(A),{span:"3 m:1"},{default:v(()=>[k("div",Fe,[k("div",Ue,[g(p(I),{size:"15"},{default:v(()=>[g(p(Se))]),_:1}),l[2]||(l[2]=N(" 堆内存使用",-1))]),g(p(Ie),{type:"line",percentage:m.value,height:14,"border-radius":7,color:m.value>85?"#e5484d":"#5b5bd6","indicator-placement":"inside"},null,8,["percentage","color"]),g(p(F),{depth:"3",style:{"font-size":"12px"}},{default:v(()=>[N(T(u.value.heapUsedMb??"—")+" / "+T(u.value.heapMaxMb??"—")+" MB ",1)]),_:1})])]),_:1}),g(p(A),{span:"3 m:1"},{default:v(()=>[k("div",Xe,[k("div",He,[g(p(I),{size:"15"},{default:v(()=>[g(p(Ae))]),_:1}),l[3]||(l[3]=N(" 已运行",-1))]),g(n,{value:x(u.value.uptimeMin)},null,8,["value"])])]),_:1}),g(p(A),{span:"3 m:1"},{default:v(()=>[k("div",Ye,[k("div",Ee,[g(p(I),{size:"15"},{default:v(()=>[g(p(Pe))]),_:1}),l[4]||(l[4]=N(" 插件 / 机器人",-1))]),g(n,{value:`${d.value.pluginsLoaded??0} / ${d.value.botsTotal??0}`},null,8,["value"])])]),_:1})]),_:1})]),_:1})])}}}),ir=we(Ke,[["__scopeId","data-v-3be2be40"]]);export{ir as default};
