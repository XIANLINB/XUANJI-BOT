import{P as _e}from"./PageHero-WvKZEzU4.js";import{_ as Se}from"./CommonChart.vue_vue_type_script_setup_true_lang-CF_VcyE7.js";import{f as A,h as o,m as ge,v as P,bo as he,bp as ve,bq as me,br as ye,bs as Ne,a2 as H,b as K,c as m,a as G,u as Be,k as be,q as Pe,bt as De,x as oe,A as O,C as u,z as N,a4 as Re,D as i,G as l,H as r,bu as ie,I as L,ah as le,W as R,Y as Q,U as V,Q as x,K as T,aj as J,P as E,S as B,R as X,F as Te,a7 as Oe,T as Z,a9 as je,aa as Ie,_ as Me}from"./index-FgWOnTD7.js";import{d as We}from"./dayjs.min-DTiHvpWZ.js";import{I as qe}from"./ImageOutline-s6eeURy3.js";import{R as Ae}from"./RefreshOutline-Cs9NdjLG.js";import{D as Fe}from"./DownloadOutline-C1NJyRan.js";import{u as Ge}from"./use-message-DPdcgKLp.js";import{_ as se}from"./Alert-BeXMGmcX.js";import{N as ae,a as U}from"./Grid-wRV2503Y.js";import{N as Ve}from"./Select-CSQENY7x.js";import{N as ne}from"./Statistic-C8AOj744.js";import{N as de}from"./Empty-D34cG5mJ.js";import{N as ue}from"./Popconfirm-BK77132i.js";import{N as Ee}from"./DataTable-pwoaZuMA.js";import{N as ee}from"./Space-DKgEX_63.js";import{N as ce}from"./Tag-BaDApJwM.js";import"./get-slot-Bk_rJcZu.js";import"./Suffix-mIHTdB6s.js";import"./Checkmark-rVuvcJrV.js";import"./use-locale-CgRSOBYo.js";import"./Checkbox-DDgk0hrl.js";import"./RadioGroup-BV99d4RK.js";import"./Input-B8PSfsSG.js";const Ye={success:o(ye,null),error:o(me,null),warning:o(ve,null),info:o(he,null)},He=A({name:"ProgressCircle",props:{clsPrefix:{type:String,required:!0},status:{type:String,required:!0},strokeWidth:{type:Number,required:!0},fillColor:[String,Object],railColor:String,railStyle:[String,Object],percentage:{type:Number,default:0},offsetDegree:{type:Number,default:0},showIndicator:{type:Boolean,required:!0},indicatorTextColor:String,unit:String,viewBoxWidth:{type:Number,required:!0},gapDegree:{type:Number,required:!0},gapOffsetDegree:{type:Number,default:0}},setup(t,{slots:g}){const p=P(()=>{const a="gradient",{fillColor:n}=t;return typeof n=="object"?`${a}-${Ne(JSON.stringify(n))}`:a});function k(a,n,c,y){const{gapDegree:z,viewBoxWidth:$,strokeWidth:S}=t,f=50,_=0,h=f,d=0,D=2*f,j=50+S/2,C=`M ${j},${j} m ${_},${h}
      a ${f},${f} 0 1 1 ${d},${-D}
      a ${f},${f} 0 1 1 ${-d},${D}`,I=Math.PI*2*f,M={stroke:y==="rail"?c:typeof t.fillColor=="object"?`url(#${p.value})`:c,strokeDasharray:`${Math.min(a,100)/100*(I-z)}px ${$*8}px`,strokeDashoffset:`-${z/2}px`,transformOrigin:n?"center":void 0,transform:n?`rotate(${n}deg)`:void 0};return{pathString:C,pathStyle:M}}const v=()=>{const a=typeof t.fillColor=="object",n=a?t.fillColor.stops[0]:"",c=a?t.fillColor.stops[1]:"";return a&&o("defs",null,o("linearGradient",{id:p.value,x1:"0%",y1:"100%",x2:"100%",y2:"0%"},o("stop",{offset:"0%","stop-color":n}),o("stop",{offset:"100%","stop-color":c})))};return()=>{const{fillColor:a,railColor:n,strokeWidth:c,offsetDegree:y,status:z,percentage:$,showIndicator:S,indicatorTextColor:f,unit:_,gapOffsetDegree:h,clsPrefix:d}=t,{pathString:D,pathStyle:j}=k(100,0,n,"rail"),{pathString:C,pathStyle:I}=k($,y,a,"fill"),M=100+c;return o("div",{class:`${d}-progress-content`,role:"none"},o("div",{class:`${d}-progress-graph`,"aria-hidden":!0},o("div",{class:`${d}-progress-graph-circle`,style:{transform:h?`rotate(${h}deg)`:void 0}},o("svg",{viewBox:`0 0 ${M} ${M}`},v(),o("g",null,o("path",{class:`${d}-progress-graph-circle-rail`,d:D,"stroke-width":c,"stroke-linecap":"round",fill:"none",style:j})),o("g",null,o("path",{class:[`${d}-progress-graph-circle-fill`,$===0&&`${d}-progress-graph-circle-fill--empty`],d:C,"stroke-width":c,"stroke-linecap":"round",fill:"none",style:I}))))),S?o("div",null,g.default?o("div",{class:`${d}-progress-custom-content`,role:"none"},g.default()):z!=="default"?o("div",{class:`${d}-progress-icon`,"aria-hidden":!0},o(ge,{clsPrefix:d},{default:()=>Ye[z]})):o("div",{class:`${d}-progress-text`,style:{color:f},role:"none"},o("span",{class:`${d}-progress-text__percentage`},$),o("span",{class:`${d}-progress-text__unit`},_))):null)}}}),Le={success:o(ye,null),error:o(me,null),warning:o(ve,null),info:o(he,null)},Xe=A({name:"ProgressLine",props:{clsPrefix:{type:String,required:!0},percentage:{type:Number,default:0},railColor:String,railStyle:[String,Object],fillColor:[String,Object],status:{type:String,required:!0},indicatorPlacement:{type:String,required:!0},indicatorTextColor:String,unit:{type:String,default:"%"},processing:{type:Boolean,required:!0},showIndicator:{type:Boolean,required:!0},height:[String,Number],railBorderRadius:[String,Number],fillBorderRadius:[String,Number]},setup(t,{slots:g}){const p=P(()=>H(t.height)),k=P(()=>{var n,c;return typeof t.fillColor=="object"?`linear-gradient(to right, ${(n=t.fillColor)===null||n===void 0?void 0:n.stops[0]} , ${(c=t.fillColor)===null||c===void 0?void 0:c.stops[1]})`:t.fillColor}),v=P(()=>t.railBorderRadius!==void 0?H(t.railBorderRadius):t.height!==void 0?H(t.height,{c:.5}):""),a=P(()=>t.fillBorderRadius!==void 0?H(t.fillBorderRadius):t.railBorderRadius!==void 0?H(t.railBorderRadius):t.height!==void 0?H(t.height,{c:.5}):"");return()=>{const{indicatorPlacement:n,railColor:c,railStyle:y,percentage:z,unit:$,indicatorTextColor:S,status:f,showIndicator:_,processing:h,clsPrefix:d}=t;return o("div",{class:`${d}-progress-content`,role:"none"},o("div",{class:`${d}-progress-graph`,"aria-hidden":!0},o("div",{class:[`${d}-progress-graph-line`,{[`${d}-progress-graph-line--indicator-${n}`]:!0}]},o("div",{class:`${d}-progress-graph-line-rail`,style:[{backgroundColor:c,height:p.value,borderRadius:v.value},y]},o("div",{class:[`${d}-progress-graph-line-fill`,h&&`${d}-progress-graph-line-fill--processing`],style:{maxWidth:`${t.percentage}%`,background:k.value,height:p.value,lineHeight:p.value,borderRadius:a.value}},n==="inside"?o("div",{class:`${d}-progress-graph-line-indicator`,style:{color:S}},g.default?g.default():`${z}${$}`):null)))),_&&n==="outside"?o("div",null,g.default?o("div",{class:`${d}-progress-custom-content`,style:{color:S},role:"none"},g.default()):f==="default"?o("div",{role:"none",class:`${d}-progress-icon ${d}-progress-icon--as-text`,style:{color:S}},z,$):o("div",{class:`${d}-progress-icon`,"aria-hidden":!0},o(ge,{clsPrefix:d},{default:()=>Le[f]}))):null)}}});function pe(t,g,p=100){return`m ${p/2} ${p/2-t} a ${t} ${t} 0 1 1 0 ${2*t} a ${t} ${t} 0 1 1 0 -${2*t}`}const Ue=A({name:"ProgressMultipleCircle",props:{clsPrefix:{type:String,required:!0},viewBoxWidth:{type:Number,required:!0},percentage:{type:Array,default:[0]},strokeWidth:{type:Number,required:!0},circleGap:{type:Number,required:!0},showIndicator:{type:Boolean,required:!0},fillColor:{type:Array,default:()=>[]},railColor:{type:Array,default:()=>[]},railStyle:{type:Array,default:()=>[]}},setup(t,{slots:g}){const p=P(()=>t.percentage.map((a,n)=>`${Math.PI*a/100*(t.viewBoxWidth/2-t.strokeWidth/2*(1+2*n)-t.circleGap*n)*2}, ${t.viewBoxWidth*8}`)),k=(v,a)=>{const n=t.fillColor[a],c=typeof n=="object"?n.stops[0]:"",y=typeof n=="object"?n.stops[1]:"";return typeof t.fillColor[a]=="object"&&o("linearGradient",{id:`gradient-${a}`,x1:"100%",y1:"0%",x2:"0%",y2:"100%"},o("stop",{offset:"0%","stop-color":c}),o("stop",{offset:"100%","stop-color":y}))};return()=>{const{viewBoxWidth:v,strokeWidth:a,circleGap:n,showIndicator:c,fillColor:y,railColor:z,railStyle:$,percentage:S,clsPrefix:f}=t;return o("div",{class:`${f}-progress-content`,role:"none"},o("div",{class:`${f}-progress-graph`,"aria-hidden":!0},o("div",{class:`${f}-progress-graph-circle`},o("svg",{viewBox:`0 0 ${v} ${v}`},o("defs",null,S.map((_,h)=>k(_,h))),S.map((_,h)=>o("g",{key:h},o("path",{class:`${f}-progress-graph-circle-rail`,d:pe(v/2-a/2*(1+2*h)-n*h,a,v),"stroke-width":a,"stroke-linecap":"round",fill:"none",style:[{strokeDashoffset:0,stroke:z[h]},$[h]]}),o("path",{class:[`${f}-progress-graph-circle-fill`,_===0&&`${f}-progress-graph-circle-fill--empty`],d:pe(v/2-a/2*(1+2*h)-n*h,a,v),"stroke-width":a,"stroke-linecap":"round",fill:"none",style:{strokeDasharray:p.value[h],strokeDashoffset:0,stroke:typeof y[h]=="object"?`url(#gradient-${h})`:y[h]}})))))),c&&g.default?o("div",null,o("div",{class:`${f}-progress-text`},g.default())):null)}}}),Ke=K([m("progress",{display:"inline-block"},[m("progress-icon",`
 color: var(--n-icon-color);
 transition: color .3s var(--n-bezier);
 `),G("line",`
 width: 100%;
 display: block;
 `,[m("progress-content",`
 display: flex;
 align-items: center;
 `,[m("progress-graph",{flex:1})]),m("progress-custom-content",{marginLeft:"14px"}),m("progress-icon",`
 width: 30px;
 padding-left: 14px;
 height: var(--n-icon-size-line);
 line-height: var(--n-icon-size-line);
 font-size: var(--n-icon-size-line);
 `,[G("as-text",`
 color: var(--n-text-color-line-outer);
 text-align: center;
 width: 40px;
 font-size: var(--n-font-size);
 padding-left: 4px;
 transition: color .3s var(--n-bezier);
 `)])]),G("circle, dashboard",{width:"120px"},[m("progress-custom-content",`
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 justify-content: center;
 `),m("progress-text",`
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
 `),m("progress-icon",`
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 display: flex;
 align-items: center;
 color: var(--n-icon-color);
 font-size: var(--n-icon-size-circle);
 `)]),G("multiple-circle",`
 width: 200px;
 color: inherit;
 `,[m("progress-text",`
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
 `)]),m("progress-content",{position:"relative"}),m("progress-graph",{position:"relative"},[m("progress-graph-circle",[K("svg",{verticalAlign:"bottom"}),m("progress-graph-circle-fill",`
 stroke: var(--n-fill-color);
 transition:
 opacity .3s var(--n-bezier),
 stroke .3s var(--n-bezier),
 stroke-dasharray .3s var(--n-bezier);
 `,[G("empty",{opacity:0})]),m("progress-graph-circle-rail",`
 transition: stroke .3s var(--n-bezier);
 overflow: hidden;
 stroke: var(--n-rail-color);
 `)]),m("progress-graph-line",[G("indicator-inside",[m("progress-graph-line-rail",`
 height: 16px;
 line-height: 16px;
 border-radius: 10px;
 `,[m("progress-graph-line-fill",`
 height: inherit;
 border-radius: 10px;
 `),m("progress-graph-line-indicator",`
 background: #0000;
 white-space: nowrap;
 text-align: right;
 margin-left: 14px;
 margin-right: 14px;
 height: inherit;
 font-size: 12px;
 color: var(--n-text-color-line-inner);
 transition: color .3s var(--n-bezier);
 `)])]),G("indicator-inside-label",`
 height: 16px;
 display: flex;
 align-items: center;
 `,[m("progress-graph-line-rail",`
 flex: 1;
 transition: background-color .3s var(--n-bezier);
 `),m("progress-graph-line-indicator",`
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
 `)]),m("progress-graph-line-rail",`
 position: relative;
 overflow: hidden;
 height: var(--n-rail-height);
 border-radius: 5px;
 background-color: var(--n-rail-color);
 transition: background-color .3s var(--n-bezier);
 `,[m("progress-graph-line-fill",`
 background: var(--n-fill-color);
 position: relative;
 border-radius: 5px;
 height: inherit;
 width: 100%;
 max-width: 0%;
 transition:
 background-color .3s var(--n-bezier),
 max-width .2s var(--n-bezier);
 `,[G("processing",[K("&::after",`
 content: "";
 background-image: var(--n-line-bg-processing);
 animation: progress-processing-animation 2s var(--n-bezier) infinite;
 `)])])])])])]),K("@keyframes progress-processing-animation",`
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
 `)]),Qe=Object.assign(Object.assign({},be.props),{processing:Boolean,type:{type:String,default:"line"},gapDegree:Number,gapOffsetDegree:Number,status:{type:String,default:"default"},railColor:[String,Array],railStyle:[String,Array],color:[String,Array,Object],viewBoxWidth:{type:Number,default:100},strokeWidth:{type:Number,default:7},percentage:[Number,Array],unit:{type:String,default:"%"},showIndicator:{type:Boolean,default:!0},indicatorPosition:{type:String,default:"outside"},indicatorPlacement:{type:String,default:"outside"},indicatorTextColor:String,circleGap:{type:Number,default:1},height:Number,borderRadius:[String,Number],fillBorderRadius:[String,Number],offsetDegree:Number}),Je=A({name:"Progress",props:Qe,setup(t){const g=P(()=>t.indicatorPlacement||t.indicatorPosition),p=P(()=>{if(t.gapDegree||t.gapDegree===0)return t.gapDegree;if(t.type==="dashboard")return 75}),{mergedClsPrefixRef:k,inlineThemeDisabled:v}=Be(t),a=be("Progress","-progress",Ke,De,t,k),n=P(()=>{const{status:y}=t,{common:{cubicBezierEaseInOut:z},self:{fontSize:$,fontSizeCircle:S,railColor:f,railHeight:_,iconSizeCircle:h,iconSizeLine:d,textColorCircle:D,textColorLineInner:j,textColorLineOuter:C,lineBgProcessing:I,fontWeightCircle:M,[oe("iconColor",y)]:q,[oe("fillColor",y)]:b}}=a.value;return{"--n-bezier":z,"--n-fill-color":b,"--n-font-size":$,"--n-font-size-circle":S,"--n-font-weight-circle":M,"--n-icon-color":q,"--n-icon-size-circle":h,"--n-icon-size-line":d,"--n-line-bg-processing":I,"--n-rail-color":f,"--n-rail-height":_,"--n-text-color-circle":D,"--n-text-color-line-inner":j,"--n-text-color-line-outer":C}}),c=v?Pe("progress",P(()=>t.status[0]),n,t):void 0;return{mergedClsPrefix:k,mergedIndicatorPlacement:g,gapDeg:p,cssVars:v?void 0:n,themeClass:c==null?void 0:c.themeClass,onRender:c==null?void 0:c.onRender}},render(){const{type:t,cssVars:g,indicatorTextColor:p,showIndicator:k,status:v,railColor:a,railStyle:n,color:c,percentage:y,viewBoxWidth:z,strokeWidth:$,mergedIndicatorPlacement:S,unit:f,borderRadius:_,fillBorderRadius:h,height:d,processing:D,circleGap:j,mergedClsPrefix:C,gapDeg:I,gapOffsetDegree:M,themeClass:q,$slots:b,onRender:Y}=this;return Y==null||Y(),o("div",{class:[q,`${C}-progress`,`${C}-progress--${t}`,`${C}-progress--${v}`],style:g,"aria-valuemax":100,"aria-valuemin":0,"aria-valuenow":y,role:t==="circle"||t==="line"||t==="dashboard"?"progressbar":"none"},t==="circle"||t==="dashboard"?o(He,{clsPrefix:C,status:v,showIndicator:k,indicatorTextColor:p,railColor:a,fillColor:c,railStyle:n,offsetDegree:this.offsetDegree,percentage:y,viewBoxWidth:z,strokeWidth:$,gapDegree:I===void 0?t==="dashboard"?75:0:I,gapOffsetDegree:M,unit:f},b):t==="line"?o(Xe,{clsPrefix:C,status:v,showIndicator:k,indicatorTextColor:p,railColor:a,fillColor:c,railStyle:n,percentage:y,processing:D,indicatorPlacement:S,unit:f,fillBorderRadius:h,railBorderRadius:_,height:d},b):t==="multiple-circle"?o(Ue,{clsPrefix:C,strokeWidth:$,railColor:a,fillColor:c,railStyle:n,viewBoxWidth:z,percentage:y,showIndicator:k,circleGap:j},b):null)}}),Ze={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},te=A({name:"DocumentOutline",render:function(g,p){return N(),O("svg",Ze,p[0]||(p[0]=[u("path",{d:"M416 221.25V416a48 48 0 0 1-48 48H144a48 48 0 0 1-48-48V96a48 48 0 0 1 48-48h98.75a32 32 0 0 1 22.62 9.37l141.26 141.26a32 32 0 0 1 9.37 22.62z",fill:"none",stroke:"currentColor","stroke-linejoin":"round","stroke-width":"32"},null,-1),u("path",{d:"M256 56v120a32 32 0 0 0 32 32h120",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1)]))}}),et={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},tt=A({name:"EyeOutline",render:function(g,p){return N(),O("svg",et,p[0]||(p[0]=[u("path",{d:"M255.66 112c-77.94 0-157.89 45.11-220.83 135.33a16 16 0 0 0-.27 17.77C82.92 340.8 161.8 400 255.66 400c92.84 0 173.34-59.38 221.79-135.25a16.14 16.14 0 0 0 0-17.47C428.89 172.28 347.8 112 255.66 112z",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),u("circle",{cx:"256",cy:"256",r:"80",fill:"none",stroke:"currentColor","stroke-miterlimit":"10","stroke-width":"32"},null,-1)]))}}),rt={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},fe=A({name:"MicOutline",render:function(g,p){return N(),O("svg",rt,p[0]||(p[0]=[u("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M192 448h128"},null,-1),u("path",{d:"M384 208v32c0 70.4-57.6 128-128 128h0c-70.4 0-128-57.6-128-128v-32",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),u("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M256 368v80"},null,-1),u("path",{d:"M256 64a63.68 63.68 0 0 0-64 64v111c0 35.2 29 65 64 65s64-29 64-65V128c0-36-28-64-64-64z",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1)]))}}),ot={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},it=A({name:"VideocamOutline",render:function(g,p){return N(),O("svg",ot,p[0]||(p[0]=[u("path",{d:"M374.79 308.78L457.5 367a16 16 0 0 0 22.5-14.62V159.62A16 16 0 0 0 457.5 145l-82.71 58.22A16 16 0 0 0 368 216.3v79.4a16 16 0 0 0 6.79 13.08z",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),u("path",{d:"M268 384H84a52.15 52.15 0 0 1-52-52V180a52.15 52.15 0 0 1 52-52h184.48A51.68 51.68 0 0 1 320 179.52V332a52.15 52.15 0 0 1-52 52z",fill:"none",stroke:"currentColor","stroke-miterlimit":"10","stroke-width":"32"},null,-1)]))}}),lt={class:"stat-row"},st={class:"stat-meta"},at={class:"stat-row"},nt={class:"stat-meta"},dt={class:"stat-row"},ut={class:"stat-meta"},ct={style:{display:"flex","align-items":"baseline",gap:"8px"}},pt={class:"type-clear-grid"},ft={class:"type-clear-info"},gt={class:"type-clear-name"},ht={class:"type-clear-cnt"},vt={key:0,class:"preview-body"},mt=["src","alt"],yt={key:1,class:"voice-preview"},bt=["src"],xt=["src"],wt={key:3,class:"voice-preview"},kt={style:{display:"flex","justify-content":"center","margin-top":"14px"}},zt={style:{"font-family":"Consolas, monospace","font-size":"12px"}},Ct=A({__name:"Files",setup(t){const g=Ge(),p=R(!1),k=R(!1),v=R([]),a=R({total:0,sizeBytes:0,quotaBytes:4*1024*1024*1024,byType:{},byTypeSize:{}}),n=R("all"),c=R(!1),y=R(null),z=R(""),$=R(""),S=R(!1),f={image:{label:"图片",color:"#07c160",icon:qe},voice:{label:"语音",color:"#f0a020",icon:fe},video:{label:"视频",color:"#e5484d",icon:it},file:{label:"文件",color:"#888780",icon:te}},_=P(()=>n.value==="all"?v.value:v.value.filter(e=>e.type===n.value)),h=P(()=>Number(a.value.quotaBytes||4*1024*1024*1024)),d=P(()=>Number(a.value.sizeBytes||0)),D=P(()=>h.value>0?Math.min(100,Math.round(d.value*100/h.value)):0),j=P(()=>{const e=a.value.byTypeSize||{},s=Object.entries(e).map(([W,w])=>{var F,re;return{name:((F=f[W])==null?void 0:F.label)??W,value:Number(w),itemStyle:{color:((re=f[W])==null?void 0:re.color)??"#888780"}}});return{tooltip:{trigger:"item",formatter:W=>`${W.name}: ${C(W.value)} (${W.percent}%)`},legend:{bottom:0},series:[{type:"pie",radius:["45%","70%"],center:["50%","44%"],itemStyle:{borderRadius:6,borderColor:"#fff",borderWidth:2},label:{formatter:`{b}
{d}%`,fontSize:11},data:s}]}});function C(e){return!e||e<=0?"0 B":e<1024?e+" B":e<1024*1024?(e/1024).toFixed(1)+" KB":e<1024*1024*1024?(e/1024/1024).toFixed(2)+" MB":(e/1024/1024/1024).toFixed(2)+" GB"}function I(e){return e>0?We(e*1e3).format("YYYY-MM-DD HH:mm"):"—"}const M=[{label:"全部类型",value:"all"},{label:"图片",value:"image"},{label:"语音",value:"voice"},{label:"视频",value:"video"},{label:"文件",value:"file"}];async function q(){p.value=!0;try{const e=await Q.getFiles();v.value=(e==null?void 0:e.files)??[],a.value={total:(e==null?void 0:e.total)??0,sizeBytes:(e==null?void 0:e.sizeBytes)??0,quotaBytes:(e==null?void 0:e.quotaBytes)??4*1024*1024*1024,byType:(e==null?void 0:e.byType)??{},byTypeSize:(e==null?void 0:e.byTypeSize)??{}}}catch(e){g.error("加载失败："+((e==null?void 0:e.message)??e))}finally{p.value=!1}}const b=R(null),Y=R(!1);function xe(e){e.url&&(b.value=e,Y.value=!0)}function we(e){y.value=e,z.value=e.path,c.value=!0}async function ke(){if(y.value)try{const e=await Q.deleteFile(y.value.path);c.value=!1,e.status==="ok"?g.success("已删除"):g.error(e.msg||"删除失败"),await q()}catch(e){g.error("删除失败："+((e==null?void 0:e.message)??e))}}async function ze(){k.value=!0;try{const e=await Q.clearFiles($.value||void 0);$.value="",g.success(e.msg||"已清理"),await q()}catch(e){g.error("清理失败："+((e==null?void 0:e.message)??e))}finally{k.value=!1}}async function Ce(){k.value=!0;try{const e=await Q.clearFiles();S.value=!1,g.success(e.msg||"已清理"),await q()}catch(e){g.error("清理失败："+((e==null?void 0:e.message)??e))}finally{k.value=!1}}const $e=[{title:"类型",key:"type",width:80,render:e=>o(ce,{size:"small",bordered:!1,type:"default"},{default:()=>{var s;return((s=f[e.type])==null?void 0:s.label)??e.type}})},{title:"文件",key:"path",minWidth:260,ellipsis:{tooltip:!0},render:e=>o("span",{style:"font-family:Consolas,monospace;font-size:12px"},{default:()=>e.path})},{title:"大小",key:"size",width:100,render:e=>C(e.size)},{title:"修改时间",key:"mtime",width:140,render:e=>I(e.mtime)},{title:"预览",key:"preview",width:90,render:e=>o(V,{size:"tiny",secondary:!0,disabled:!e.url,onClick:()=>xe(e)},{default:()=>o(ee,{size:4,align:"center"},{default:()=>[o(T,{size:14},{default:()=>o(tt)}),"查看"]})})},{title:"操作",key:"op",width:90,render:e=>o(V,{size:"tiny",type:"error",ghost:!0,onClick:()=>we(e)},{default:()=>"删除"})}];return Re(q),(e,s)=>{var W;return N(),O("div",null,[i(_e,{title:"文件存储",subtitle:"媒体本地存储 · 在线预览 · 按类型清理（磁盘管理闭环）",icon:r(ie)},{default:l(()=>[i(r(Ve),{value:n.value,"onUpdate:value":s[0]||(s[0]=w=>n.value=w),options:M,size:"small",style:{width:"130px"}},null,8,["value"]),i(r(V),{secondary:"",loading:p.value,onClick:q},{icon:l(()=>[i(r(T),null,{default:l(()=>[i(r(Ae))]),_:1})]),default:l(()=>[s[4]||(s[4]=x(" 刷新 ",-1))]),_:1},8,["loading"])]),_:1},8,["icon"]),i(r(se),{type:"info","show-icon":!0,style:{"margin-bottom":"14px"}},{default:l(()=>[...s[5]||(s[5]=[x(" 媒体为",-1),u("b",null,"框架级共享存储",-1),x("（",-1),u("span",{style:{"font-family":"monospace"}},"data/xuanji/media",-1),x("，内容哈希去重），不区分机器人； 图片/视频可直接预览，语音若浏览器不支持该格式（如 silk/amr）会显示提示，可下载查看。 ",-1)])]),_:1}),i(r(ae),{cols:3,"x-gap":12,"y-gap":12,responsive:"screen","item-responsive":"",style:{"margin-bottom":"14px"}},{default:l(()=>[i(r(U),{span:"3 m:1"},{default:l(()=>[i(r(L),{class:"stat-card",hoverable:""},{default:l(()=>[u("div",lt,[i(r(T),{size:"22",color:"#5b5bd6"},{default:l(()=>[i(r(ie))]),_:1}),u("div",st,[s[6]||(s[6]=u("div",{class:"stat-label"},"文件总数",-1)),i(r(ne),{value:a.value.total,style:{"--n-value-font-size":"22px"}},null,8,["value"])])])]),_:1})]),_:1}),i(r(U),{span:"3 m:1"},{default:l(()=>[i(r(L),{class:"stat-card",hoverable:""},{default:l(()=>[u("div",at,[i(r(T),{size:"22",color:"#fa8c16"},{default:l(()=>[i(r(te))]),_:1}),u("div",nt,[s[7]||(s[7]=u("div",{class:"stat-label"},"总占用",-1)),i(r(ne),{value:C(d.value),style:{"--n-value-font-size":"22px"}},null,8,["value"])])])]),_:1})]),_:1}),i(r(U),{span:"3 m:1"},{default:l(()=>[i(r(L),{class:"stat-card",hoverable:""},{default:l(()=>[u("div",dt,[i(r(T),{size:"22",color:"#1E88E5"},{default:l(()=>[i(r(J))]),_:1}),u("div",ut,[s[8]||(s[8]=u("div",{class:"stat-label"},"配额",-1)),u("div",ct,[i(r(E),{style:{"font-size":"22px","font-weight":"600"}},{default:l(()=>[x(B(C(h.value)),1)]),_:1}),i(r(E),{depth:"3",style:{"font-size":"12px"}},{default:l(()=>[x("已用 "+B(D.value)+"%",1)]),_:1})])])]),i(r(Je),{percentage:D.value,color:D.value>90?"#e5484d":"#1E88E5",height:6,"border-radius":3,style:{"margin-top":"8px"}},null,8,["percentage","color"]),s[9]||(s[9]=u("div",{class:"stat-sub"},"超限自动删最旧（media.storage.max_bytes 可配）",-1))]),_:1})]),_:1})]),_:1}),i(r(ae),{cols:24,"x-gap":12,"y-gap":12,responsive:"screen","item-responsive":"",style:{"margin-bottom":"14px"}},{default:l(()=>[i(r(U),{span:"24 m:9"},{default:l(()=>[i(r(L),{bordered:!0},{header:l(()=>[...s[10]||(s[10]=[u("span",{style:{"font-weight":"600"}},"类型占用分布",-1)])]),"header-extra":l(()=>[i(r(E),{depth:"3",style:{"font-size":"11.5px"}},{default:l(()=>[...s[11]||(s[11]=[x("各类型文件占用磁盘字节",-1)])]),_:1})]),default:l(()=>[Object.keys(a.value.byTypeSize||{}).length?(N(),X(Se,{key:0,option:j.value,height:"240px"},null,8,["option"])):(N(),X(r(de),{key:1,description:"暂无文件",style:{padding:"40px 0"}}))]),_:1})]),_:1}),i(r(U),{span:"24 m:15"},{default:l(()=>[i(r(L),{bordered:!0},{header:l(()=>[...s[12]||(s[12]=[u("span",{style:{"font-weight":"600"}},"按类型清理",-1)])]),"header-extra":l(()=>[i(r(ue),{onPositiveClick:Ce},{trigger:l(()=>[i(r(V),{size:"small",type:"error",tertiary:"",disabled:!a.value.total,loading:k.value},{icon:l(()=>[i(r(T),null,{default:l(()=>[i(r(J))]),_:1})]),default:l(()=>[s[13]||(s[13]=x(" 一键清理全部 ",-1))]),_:1},8,["disabled","loading"])]),default:l(()=>[x(" 将删除全部 "+B(a.value.total)+" 个媒体文件，不可恢复。确认？ ",1)]),_:1})]),default:l(()=>[u("div",pt,[(N(),O(Te,null,Oe(f,(w,F)=>u("div",{key:F,class:"type-clear-item"},[u("div",{class:"type-clear-icon",style:je({background:w.color+"15",color:w.color})},[i(r(T),{size:"26"},{default:l(()=>[(N(),X(Ie(w.icon)))]),_:2},1024)],4),u("div",ft,[u("div",gt,B(w.label),1),u("div",ht,B(Number((a.value.byType||{})[F]||0))+" 个 · "+B(C(Number((a.value.byTypeSize||{})[F]||0))),1)]),i(r(ue),{onPositiveClick:ze},{trigger:l(()=>[i(r(V),{size:"tiny",type:"error",ghost:"",disabled:!Number((a.value.byType||{})[F]||0)},{default:l(()=>[x(" 清理"+B(w.label),1)]),_:2},1032,["disabled"])]),default:l(()=>[x(" 将删除全部 "+B(w.label)+" 文件（"+B(Number((a.value.byType||{})[F]||0))+" 个），不可恢复。确认？ ",1)]),_:2},1024)])),64))])]),_:1})]),_:1})]),_:1}),i(r(L),{bordered:!0},{header:l(()=>[i(r(ee),{align:"center",size:8},{default:l(()=>[s[14]||(s[14]=u("span",{style:{"font-weight":"600"}},"文件列表",-1)),n.value!=="all"?(N(),X(r(ce),{key:0,bordered:!1,size:"small",type:"info"},{default:l(()=>{var w;return[x(B(((w=f[n.value])==null?void 0:w.label)??n.value)+" · "+B(_.value.length)+" 个 ",1)]}),_:1})):Z("",!0)]),_:1})]),default:l(()=>[i(r(Ee),{columns:$e,data:_.value,bordered:!1,size:"small",loading:p.value,"row-key":w=>w.path},null,8,["data","loading","row-key"]),!p.value&&!_.value.length?(N(),X(r(de),{key:0,description:"暂无媒体文件",style:{padding:"30px 0"}})):Z("",!0)]),_:1}),i(r(le),{show:Y.value,"onUpdate:show":s[1]||(s[1]=w=>Y.value=w),preset:"card",title:b.value?((W=f[b.value.type])==null?void 0:W.label)+" · "+b.value.path:"",style:{width:"640px","max-width":"94vw"},bordered:!1},{default:l(()=>[b.value?(N(),O("div",vt,[b.value.type==="image"?(N(),O("img",{key:0,src:b.value.url,class:"preview-img",alt:b.value.path},null,8,mt)):b.value.type==="voice"?(N(),O("div",yt,[i(r(T),{size:"48",color:"#f0a020"},{default:l(()=>[i(r(fe))]),_:1}),i(r(E),{style:{"font-size":"14px","margin-bottom":"8px"}},{default:l(()=>[x(B(b.value.path),1)]),_:1}),u("audio",{src:b.value.url,controls:"",style:{width:"100%","max-width":"420px"}}," 你的浏览器不支持 audio 播放 ",8,bt),i(r(E),{depth:"3",style:{"font-size":"11.5px","margin-top":"8px"}},{default:l(()=>[...s[15]||(s[15]=[x(" 若无法播放，可能是 silk/amr 等 QQ 语音格式，浏览器不支持，可下载后用本地播放器打开 ",-1)])]),_:1})])):b.value.type==="video"?(N(),O("video",{key:2,src:b.value.url,controls:"",class:"preview-video"}," 你的浏览器不支持 video 播放 ",8,xt)):(N(),O("div",wt,[i(r(T),{size:"48",color:"#888780"},{default:l(()=>[i(r(te))]),_:1}),i(r(E),{style:{"font-size":"14px"}},{default:l(()=>[x(B(b.value.path),1)]),_:1}),i(r(E),{depth:"3",style:{"font-size":"12px"}},{default:l(()=>[x(B(C(b.value.size))+" · 该类型不支持内嵌预览",1)]),_:1})])),u("div",kt,[i(r(V),{size:"small",tag:"a",href:b.value.url,target:"_blank",download:""},{icon:l(()=>[i(r(T),null,{default:l(()=>[i(r(Fe))]),_:1})]),default:l(()=>[s[16]||(s[16]=x(" 下载文件 ",-1))]),_:1},8,["href"])])])):Z("",!0)]),_:1},8,["show","title"]),i(r(le),{show:c.value,"onUpdate:show":s[3]||(s[3]=w=>c.value=w),preset:"card",title:`删除文件 · ${z.value}`,style:{width:"460px","max-width":"92vw"},bordered:!1},{footer:l(()=>[i(r(ee),{justify:"end"},{default:l(()=>[i(r(V),{size:"small",onClick:s[2]||(s[2]=w=>c.value=!1)},{default:l(()=>[...s[20]||(s[20]=[x("取消",-1)])]),_:1}),i(r(V),{size:"small",type:"error",onClick:ke},{icon:l(()=>[i(r(T),null,{default:l(()=>[i(r(J))]),_:1})]),default:l(()=>[s[21]||(s[21]=x(" 确认删除 ",-1))]),_:1})]),_:1})]),default:l(()=>[i(r(se),{type:"warning","show-icon":!0,style:{"margin-bottom":"12px"}},{default:l(()=>[...s[17]||(s[17]=[x("此操作不可恢复",-1)])]),_:1}),i(r(E),{depth:"2",style:{"font-size":"13px","line-height":"1.7"}},{default:l(()=>[s[18]||(s[18]=x(" 将永久删除媒体目录下的文件：",-1)),s[19]||(s[19]=u("br",null,null,-1)),u("span",zt,B(z.value),1)]),_:1})]),_:1},8,["show","title"])])}}}),Ut=Me(Ct,[["__scopeId","data-v-abcc69aa"]]);export{Ut as default};
