import{f as Z,h as b,bN as vt,_ as L,bO as ht,bP as me,bQ as gt,bR as ze,l as xt,t as mt,o as $e,aT as yt,F as wt,m as St,bS as Ct,ba as Rt,v as G,bT as Tt,c as r,a as i,b as g,d as z,e as zt,g as re,r as ye,bH as ne,u as $t,k as Pe,b5 as Pt,aw as oe,ab as Wt,bU as _t,ap as Et,q as Lt,am as we,bV as At,aP as Bt,bW as kt,bK as jt,bX as Ht,aD as ie,x as j,bY as J,aV as Ot,p as It,w as H,y as Q}from"./index-CPm64mFd.js";import{A as Ft}from"./Add-C9xKDAWM.js";import{t as Se}from"./toNumber-Cp5dvN-o.js";const Dt=me(".v-x-scroll",{overflow:"auto",scrollbarWidth:"none"},[me("&::-webkit-scrollbar",{width:0,height:0})]),Mt=Z({name:"XScroll",props:{disabled:Boolean,onScroll:Function},setup(){const e=L(null);function o(d){!(d.currentTarget.offsetWidth<d.currentTarget.scrollWidth)||d.deltaY===0||(d.currentTarget.scrollLeft+=d.deltaY+d.deltaX,d.preventDefault())}const c=ht();return Dt.mount({id:"vueuc/x-scroll",head:!0,anchorMetaName:vt,ssr:c}),Object.assign({selfRef:e,handleWheel:o},{scrollTo(...d){var x;(x=e.value)===null||x===void 0||x.scrollTo(...d)}})},render(){return b("div",{ref:"selfRef",onScroll:this.onScroll,onWheel:this.disabled?void 0:this.handleWheel,class:"v-x-scroll"},this.$slots)}});var se=function(){return gt.Date.now()},Nt="Expected a function",Vt=Math.max,Xt=Math.min;function Ut(e,o,c){var u,d,x,v,f,h,m=0,y=!1,T=!1,E=!0;if(typeof e!="function")throw new TypeError(Nt);o=Se(o)||0,ze(c)&&(y=!!c.leading,T="maxWait"in c,x=T?Vt(Se(c.maxWait)||0,o):x,E="trailing"in c?!!c.trailing:E);function C(s){var _=u,F=d;return u=d=void 0,m=s,v=e.apply(F,_),v}function S(s){return m=s,f=setTimeout(W,o),y?C(s):v}function R(s){var _=s-h,F=s-m,D=o-_;return T?Xt(D,x-F):D}function P(s){var _=s-h,F=s-m;return h===void 0||_>=o||_<0||T&&F>=x}function W(){var s=se();if(P(s))return $(s);f=setTimeout(W,R(s))}function $(s){return f=void 0,E&&u?C(s):(u=d=void 0,v)}function O(){f!==void 0&&clearTimeout(f),m=0,u=h=d=f=void 0}function k(){return f===void 0?v:$(se())}function p(){var s=se(),_=P(s);if(u=arguments,d=this,h=s,_){if(f===void 0)return S(h);if(T)return clearTimeout(f),f=setTimeout(W,o),C(h)}return f===void 0&&(f=setTimeout(W,o)),v}return p.cancel=O,p.flush=k,p}var Gt="Expected a function";function Yt(e,o,c){var u=!0,d=!0;if(typeof e!="function")throw new TypeError(Gt);return ze(c)&&(u="leading"in c?!!c.leading:u,d="trailing"in c?!!c.trailing:d),Ut(e,o,{leading:u,maxWait:o,trailing:d})}const ce=xt("n-tabs"),We={tab:[String,Number,Object,Function],name:{type:[String,Number],required:!0},disabled:Boolean,displayDirective:{type:String,default:"if"},closable:{type:Boolean,default:void 0},tabProps:Object,label:[String,Number,Object,Function]},ta=Z({__TAB_PANE__:!0,name:"TabPane",alias:["TabPanel"],props:We,slots:Object,setup(e){const o=$e(ce,null);return o||mt("tab-pane","`n-tab-pane` must be placed inside `n-tabs`."),{style:o.paneStyleRef,class:o.paneClassRef,mergedClsPrefix:o.mergedClsPrefixRef}},render(){return b("div",{class:[`${this.mergedClsPrefix}-tab-pane`,this.class],style:this.style},this.$slots)}}),Kt=Object.assign({internalLeftPadded:Boolean,internalAddable:Boolean,internalCreatedByPane:Boolean},Tt(We,["displayDirective"])),be=Z({__TAB__:!0,inheritAttrs:!1,name:"Tab",props:Kt,setup(e){const{mergedClsPrefixRef:o,valueRef:c,typeRef:u,closableRef:d,tabStyleRef:x,addTabStyleRef:v,tabClassRef:f,addTabClassRef:h,tabChangeIdRef:m,onBeforeLeaveRef:y,triggerRef:T,handleAdd:E,activateTab:C,handleClose:S}=$e(ce);return{trigger:T,mergedClosable:G(()=>{if(e.internalAddable)return!1;const{closable:R}=e;return R===void 0?d.value:R}),style:x,addStyle:v,tabClass:f,addTabClass:h,clsPrefix:o,value:c,type:u,handleClose(R){R.stopPropagation(),!e.disabled&&S(e.name)},activateTab(){if(e.disabled)return;if(e.internalAddable){E();return}const{name:R}=e,P=++m.id;if(R!==c.value){const{value:W}=y;W?Promise.resolve(W(e.name,c.value)).then($=>{$&&m.id===P&&C(R)}):C(R)}}}},render(){const{internalAddable:e,clsPrefix:o,name:c,disabled:u,label:d,tab:x,value:v,mergedClosable:f,trigger:h,$slots:{default:m}}=this,y=d??x;return b("div",{class:`${o}-tabs-tab-wrapper`},this.internalLeftPadded?b("div",{class:`${o}-tabs-tab-pad`}):null,b("div",Object.assign({key:c,"data-name":c,"data-disabled":u?!0:void 0},yt({class:[`${o}-tabs-tab`,v===c&&`${o}-tabs-tab--active`,u&&`${o}-tabs-tab--disabled`,f&&`${o}-tabs-tab--closable`,e&&`${o}-tabs-tab--addable`,e?this.addTabClass:this.tabClass],onClick:h==="click"?this.activateTab:void 0,onMouseenter:h==="hover"?this.activateTab:void 0,style:e?this.addStyle:this.style},this.internalCreatedByPane?this.tabProps||{}:this.$attrs)),b("span",{class:`${o}-tabs-tab__label`},e?b(wt,null,b("div",{class:`${o}-tabs-tab__height-placeholder`}," "),b(St,{clsPrefix:o},{default:()=>b(Ft,null)})):m?m():typeof y=="object"?y:Ct(y??c)),f&&this.type==="card"?b(Rt,{clsPrefix:o,class:`${o}-tabs-tab__close`,onClick:this.handleClose,disabled:u}):null))}}),qt=r("tabs",`
 box-sizing: border-box;
 width: 100%;
 display: flex;
 flex-direction: column;
 transition:
 background-color .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
`,[i("segment-type",[r("tabs-rail",[g("&.transition-disabled",[r("tabs-capsule",`
 transition: none;
 `)])])]),i("top",[r("tab-pane",`
 padding: var(--n-pane-padding-top) var(--n-pane-padding-right) var(--n-pane-padding-bottom) var(--n-pane-padding-left);
 `)]),i("left",[r("tab-pane",`
 padding: var(--n-pane-padding-right) var(--n-pane-padding-bottom) var(--n-pane-padding-left) var(--n-pane-padding-top);
 `)]),i("left, right",`
 flex-direction: row;
 `,[r("tabs-bar",`
 width: 2px;
 right: 0;
 transition:
 top .2s var(--n-bezier),
 max-height .2s var(--n-bezier),
 background-color .3s var(--n-bezier);
 `),r("tabs-tab",`
 padding: var(--n-tab-padding-vertical); 
 `)]),i("right",`
 flex-direction: row-reverse;
 `,[r("tab-pane",`
 padding: var(--n-pane-padding-left) var(--n-pane-padding-top) var(--n-pane-padding-right) var(--n-pane-padding-bottom);
 `),r("tabs-bar",`
 left: 0;
 `)]),i("bottom",`
 flex-direction: column-reverse;
 justify-content: flex-end;
 `,[r("tab-pane",`
 padding: var(--n-pane-padding-bottom) var(--n-pane-padding-right) var(--n-pane-padding-top) var(--n-pane-padding-left);
 `),r("tabs-bar",`
 top: 0;
 `)]),r("tabs-rail",`
 position: relative;
 padding: 3px;
 border-radius: var(--n-tab-border-radius);
 width: 100%;
 background-color: var(--n-color-segment);
 transition: background-color .3s var(--n-bezier);
 display: flex;
 align-items: center;
 `,[r("tabs-capsule",`
 border-radius: var(--n-tab-border-radius);
 position: absolute;
 pointer-events: none;
 background-color: var(--n-tab-color-segment);
 box-shadow: 0 1px 3px 0 rgba(0, 0, 0, .08);
 transition: transform 0.3s var(--n-bezier);
 `),r("tabs-tab-wrapper",`
 flex-basis: 0;
 flex-grow: 1;
 display: flex;
 align-items: center;
 justify-content: center;
 `,[r("tabs-tab",`
 overflow: hidden;
 border-radius: var(--n-tab-border-radius);
 width: 100%;
 display: flex;
 align-items: center;
 justify-content: center;
 `,[i("active",`
 font-weight: var(--n-font-weight-strong);
 color: var(--n-tab-text-color-active);
 `),g("&:hover",`
 color: var(--n-tab-text-color-hover);
 `)])])]),i("flex",[r("tabs-nav",`
 width: 100%;
 position: relative;
 `,[r("tabs-wrapper",`
 width: 100%;
 `,[r("tabs-tab",`
 margin-right: 0;
 `)])])]),r("tabs-nav",`
 box-sizing: border-box;
 line-height: 1.5;
 display: flex;
 transition: border-color .3s var(--n-bezier);
 `,[z("prefix, suffix",`
 display: flex;
 align-items: center;
 `),z("prefix","padding-right: 16px;"),z("suffix","padding-left: 16px;")]),i("top, bottom",[g(">",[r("tabs-nav",[r("tabs-nav-scroll-wrapper",[g("&::before",`
 top: 0;
 bottom: 0;
 left: 0;
 width: 20px;
 `),g("&::after",`
 top: 0;
 bottom: 0;
 right: 0;
 width: 20px;
 `),i("shadow-start",[g("&::before",`
 box-shadow: inset 10px 0 8px -8px rgba(0, 0, 0, .12);
 `)]),i("shadow-end",[g("&::after",`
 box-shadow: inset -10px 0 8px -8px rgba(0, 0, 0, .12);
 `)])])])])]),i("left, right",[r("tabs-nav-scroll-content",`
 flex-direction: column;
 `),g(">",[r("tabs-nav",[r("tabs-nav-scroll-wrapper",[g("&::before",`
 top: 0;
 left: 0;
 right: 0;
 height: 20px;
 `),g("&::after",`
 bottom: 0;
 left: 0;
 right: 0;
 height: 20px;
 `),i("shadow-start",[g("&::before",`
 box-shadow: inset 0 10px 8px -8px rgba(0, 0, 0, .12);
 `)]),i("shadow-end",[g("&::after",`
 box-shadow: inset 0 -10px 8px -8px rgba(0, 0, 0, .12);
 `)])])])])]),r("tabs-nav-scroll-wrapper",`
 flex: 1;
 position: relative;
 overflow: hidden;
 `,[r("tabs-nav-y-scroll",`
 height: 100%;
 width: 100%;
 overflow-y: auto; 
 scrollbar-width: none;
 `,[g("&::-webkit-scrollbar, &::-webkit-scrollbar-track-piece, &::-webkit-scrollbar-thumb",`
 width: 0;
 height: 0;
 display: none;
 `)]),g("&::before, &::after",`
 transition: box-shadow .3s var(--n-bezier);
 pointer-events: none;
 content: "";
 position: absolute;
 z-index: 1;
 `)]),r("tabs-nav-scroll-content",`
 display: flex;
 position: relative;
 min-width: 100%;
 min-height: 100%;
 width: fit-content;
 box-sizing: border-box;
 `),r("tabs-wrapper",`
 display: inline-flex;
 flex-wrap: nowrap;
 position: relative;
 `),r("tabs-tab-wrapper",`
 display: flex;
 flex-wrap: nowrap;
 flex-shrink: 0;
 flex-grow: 0;
 `),r("tabs-tab",`
 cursor: pointer;
 white-space: nowrap;
 flex-wrap: nowrap;
 display: inline-flex;
 align-items: center;
 color: var(--n-tab-text-color);
 font-size: var(--n-tab-font-size);
 background-clip: padding-box;
 padding: var(--n-tab-padding);
 transition:
 box-shadow .3s var(--n-bezier),
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 `,[i("disabled",{cursor:"not-allowed"}),z("close",`
 margin-left: 6px;
 transition:
 background-color .3s var(--n-bezier),
 color .3s var(--n-bezier);
 `),z("label",`
 display: flex;
 align-items: center;
 z-index: 1;
 `)]),r("tabs-bar",`
 position: absolute;
 bottom: 0;
 height: 2px;
 border-radius: 1px;
 background-color: var(--n-bar-color);
 transition:
 left .2s var(--n-bezier),
 max-width .2s var(--n-bezier),
 opacity .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 `,[g("&.transition-disabled",`
 transition: none;
 `),i("disabled",`
 background-color: var(--n-tab-text-color-disabled)
 `)]),r("tabs-pane-wrapper",`
 position: relative;
 overflow: hidden;
 transition: max-height .2s var(--n-bezier);
 `),r("tab-pane",`
 color: var(--n-pane-text-color);
 width: 100%;
 transition:
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier),
 opacity .2s var(--n-bezier);
 left: 0;
 right: 0;
 top: 0;
 `,[g("&.next-transition-leave-active, &.prev-transition-leave-active, &.next-transition-enter-active, &.prev-transition-enter-active",`
 transition:
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier),
 transform .2s var(--n-bezier),
 opacity .2s var(--n-bezier);
 `),g("&.next-transition-leave-active, &.prev-transition-leave-active",`
 position: absolute;
 `),g("&.next-transition-enter-from, &.prev-transition-leave-to",`
 transform: translateX(32px);
 opacity: 0;
 `),g("&.next-transition-leave-to, &.prev-transition-enter-from",`
 transform: translateX(-32px);
 opacity: 0;
 `),g("&.next-transition-leave-from, &.next-transition-enter-to, &.prev-transition-leave-from, &.prev-transition-enter-to",`
 transform: translateX(0);
 opacity: 1;
 `)]),r("tabs-tab-pad",`
 box-sizing: border-box;
 width: var(--n-tab-gap);
 flex-grow: 0;
 flex-shrink: 0;
 `),i("line-type, bar-type",[r("tabs-tab",`
 font-weight: var(--n-tab-font-weight);
 box-sizing: border-box;
 vertical-align: bottom;
 `,[g("&:hover",{color:"var(--n-tab-text-color-hover)"}),i("active",`
 color: var(--n-tab-text-color-active);
 font-weight: var(--n-tab-font-weight-active);
 `),i("disabled",{color:"var(--n-tab-text-color-disabled)"})])]),r("tabs-nav",[i("line-type",[i("top",[z("prefix, suffix",`
 border-bottom: 1px solid var(--n-tab-border-color);
 `),r("tabs-nav-scroll-content",`
 border-bottom: 1px solid var(--n-tab-border-color);
 `),r("tabs-bar",`
 bottom: -1px;
 `)]),i("left",[z("prefix, suffix",`
 border-right: 1px solid var(--n-tab-border-color);
 `),r("tabs-nav-scroll-content",`
 border-right: 1px solid var(--n-tab-border-color);
 `),r("tabs-bar",`
 right: -1px;
 `)]),i("right",[z("prefix, suffix",`
 border-left: 1px solid var(--n-tab-border-color);
 `),r("tabs-nav-scroll-content",`
 border-left: 1px solid var(--n-tab-border-color);
 `),r("tabs-bar",`
 left: -1px;
 `)]),i("bottom",[z("prefix, suffix",`
 border-top: 1px solid var(--n-tab-border-color);
 `),r("tabs-nav-scroll-content",`
 border-top: 1px solid var(--n-tab-border-color);
 `),r("tabs-bar",`
 top: -1px;
 `)]),z("prefix, suffix",`
 transition: border-color .3s var(--n-bezier);
 `),r("tabs-nav-scroll-content",`
 transition: border-color .3s var(--n-bezier);
 `),r("tabs-bar",`
 border-radius: 0;
 `)]),i("card-type",[z("prefix, suffix",`
 transition: border-color .3s var(--n-bezier);
 `),r("tabs-pad",`
 flex-grow: 1;
 transition: border-color .3s var(--n-bezier);
 `),r("tabs-tab-pad",`
 transition: border-color .3s var(--n-bezier);
 `),r("tabs-tab",`
 font-weight: var(--n-tab-font-weight);
 border: 1px solid var(--n-tab-border-color);
 background-color: var(--n-tab-color);
 box-sizing: border-box;
 position: relative;
 vertical-align: bottom;
 display: flex;
 justify-content: space-between;
 font-size: var(--n-tab-font-size);
 color: var(--n-tab-text-color);
 `,[i("addable",`
 padding-left: 8px;
 padding-right: 8px;
 font-size: 16px;
 justify-content: center;
 `,[z("height-placeholder",`
 width: 0;
 font-size: var(--n-tab-font-size);
 `),zt("disabled",[g("&:hover",`
 color: var(--n-tab-text-color-hover);
 `)])]),i("closable","padding-right: 8px;"),i("active",`
 background-color: #0000;
 font-weight: var(--n-tab-font-weight-active);
 color: var(--n-tab-text-color-active);
 `),i("disabled","color: var(--n-tab-text-color-disabled);")])]),i("left, right",`
 flex-direction: column; 
 `,[z("prefix, suffix",`
 padding: var(--n-tab-padding-vertical);
 `),r("tabs-wrapper",`
 flex-direction: column;
 `),r("tabs-tab-wrapper",`
 flex-direction: column;
 `,[r("tabs-tab-pad",`
 height: var(--n-tab-gap-vertical);
 width: 100%;
 `)])]),i("top",[i("card-type",[r("tabs-scroll-padding","border-bottom: 1px solid var(--n-tab-border-color);"),z("prefix, suffix",`
 border-bottom: 1px solid var(--n-tab-border-color);
 `),r("tabs-tab",`
 border-top-left-radius: var(--n-tab-border-radius);
 border-top-right-radius: var(--n-tab-border-radius);
 `,[i("active",`
 border-bottom: 1px solid #0000;
 `)]),r("tabs-tab-pad",`
 border-bottom: 1px solid var(--n-tab-border-color);
 `),r("tabs-pad",`
 border-bottom: 1px solid var(--n-tab-border-color);
 `)])]),i("left",[i("card-type",[r("tabs-scroll-padding","border-right: 1px solid var(--n-tab-border-color);"),z("prefix, suffix",`
 border-right: 1px solid var(--n-tab-border-color);
 `),r("tabs-tab",`
 border-top-left-radius: var(--n-tab-border-radius);
 border-bottom-left-radius: var(--n-tab-border-radius);
 `,[i("active",`
 border-right: 1px solid #0000;
 `)]),r("tabs-tab-pad",`
 border-right: 1px solid var(--n-tab-border-color);
 `),r("tabs-pad",`
 border-right: 1px solid var(--n-tab-border-color);
 `)])]),i("right",[i("card-type",[r("tabs-scroll-padding","border-left: 1px solid var(--n-tab-border-color);"),z("prefix, suffix",`
 border-left: 1px solid var(--n-tab-border-color);
 `),r("tabs-tab",`
 border-top-right-radius: var(--n-tab-border-radius);
 border-bottom-right-radius: var(--n-tab-border-radius);
 `,[i("active",`
 border-left: 1px solid #0000;
 `)]),r("tabs-tab-pad",`
 border-left: 1px solid var(--n-tab-border-color);
 `),r("tabs-pad",`
 border-left: 1px solid var(--n-tab-border-color);
 `)])]),i("bottom",[i("card-type",[r("tabs-scroll-padding","border-top: 1px solid var(--n-tab-border-color);"),z("prefix, suffix",`
 border-top: 1px solid var(--n-tab-border-color);
 `),r("tabs-tab",`
 border-bottom-left-radius: var(--n-tab-border-radius);
 border-bottom-right-radius: var(--n-tab-border-radius);
 `,[i("active",`
 border-top: 1px solid #0000;
 `)]),r("tabs-tab-pad",`
 border-top: 1px solid var(--n-tab-border-color);
 `),r("tabs-pad",`
 border-top: 1px solid var(--n-tab-border-color);
 `)])])])]),le=Yt,Jt=Object.assign(Object.assign({},Pe.props),{value:[String,Number],defaultValue:[String,Number],trigger:{type:String,default:"click"},type:{type:String,default:"bar"},closable:Boolean,justifyContent:String,size:String,placement:{type:String,default:"top"},tabStyle:[String,Object],tabClass:String,addTabStyle:[String,Object],addTabClass:String,barWidth:Number,paneClass:String,paneStyle:[String,Object],paneWrapperClass:String,paneWrapperStyle:[String,Object],addable:[Boolean,Object],tabsPadding:{type:Number,default:0},animated:Boolean,onBeforeLeave:Function,onAdd:Function,"onUpdate:value":[Function,Array],onUpdateValue:[Function,Array],onClose:[Function,Array],labelSize:String,activeName:[String,Number],onActiveNameChange:[Function,Array]}),aa=Z({name:"Tabs",props:Jt,slots:Object,setup(e,{slots:o}){var c,u,d,x;const{mergedClsPrefixRef:v,inlineThemeDisabled:f,mergedComponentPropsRef:h}=$t(e),m=Pe("Tabs","-tabs",qt,Ht,e,v),y=L(null),T=L(null),E=L(null),C=L(null),S=L(null),R=L(null),P=L(!0),W=L(!0),$=we(e,["labelSize","size"]),O=G(()=>{var t,a;if($.value)return $.value;const n=(a=(t=h==null?void 0:h.value)===null||t===void 0?void 0:t.Tabs)===null||a===void 0?void 0:a.size;return n||"medium"}),k=we(e,["activeName","value"]),p=L((u=(c=k.value)!==null&&c!==void 0?c:e.defaultValue)!==null&&u!==void 0?u:o.default?(x=(d=re(o.default())[0])===null||d===void 0?void 0:d.props)===null||x===void 0?void 0:x.name:null),s=Pt(k,p),_={id:0},F=G(()=>{if(!(!e.justifyContent||e.type==="card"))return{display:"flex",justifyContent:e.justifyContent}});oe(s,()=>{_.id=0,Y(),pe()});function D(){var t;const{value:a}=s;return a===null?null:(t=y.value)===null||t===void 0?void 0:t.querySelector(`[data-name="${a}"]`)}function _e(t){if(e.type==="card")return;const{value:a}=T;if(!a)return;const n=a.style.opacity==="0";if(t){const l=`${v.value}-tabs-bar--disabled`,{barWidth:w,placement:A}=e;if(t.dataset.disabled==="true"?a.classList.add(l):a.classList.remove(l),["top","bottom"].includes(A)){if(fe(["top","maxHeight","height"]),typeof w=="number"&&t.offsetWidth>=w){const B=Math.floor((t.offsetWidth-w)/2)+t.offsetLeft;a.style.left=`${B}px`,a.style.maxWidth=`${w}px`}else a.style.left=`${t.offsetLeft}px`,a.style.maxWidth=`${t.offsetWidth}px`;a.style.width="8192px",n&&(a.style.transition="none"),a.offsetWidth,n&&(a.style.transition="",a.style.opacity="1")}else{if(fe(["left","maxWidth","width"]),typeof w=="number"&&t.offsetHeight>=w){const B=Math.floor((t.offsetHeight-w)/2)+t.offsetTop;a.style.top=`${B}px`,a.style.maxHeight=`${w}px`}else a.style.top=`${t.offsetTop}px`,a.style.maxHeight=`${t.offsetHeight}px`;a.style.height="8192px",n&&(a.style.transition="none"),a.offsetHeight,n&&(a.style.transition="",a.style.opacity="1")}}}function Ee(){if(e.type==="card")return;const{value:t}=T;t&&(t.style.opacity="0")}function fe(t){const{value:a}=T;if(a)for(const n of t)a.style[n]=""}function Y(){if(e.type==="card")return;const t=D();t?_e(t):Ee()}function pe(){var t;const a=(t=S.value)===null||t===void 0?void 0:t.$el;if(!a)return;const n=D();if(!n)return;const{scrollLeft:l,offsetWidth:w}=a,{offsetLeft:A,offsetWidth:B}=n;l>A?a.scrollTo({top:0,left:A,behavior:"smooth"}):A+B>l+w&&a.scrollTo({top:0,left:A+B-w,behavior:"smooth"})}const K=L(null);let ee=0,I=null;function Le(t){const a=K.value;if(a){ee=t.getBoundingClientRect().height;const n=`${ee}px`,l=()=>{a.style.height=n,a.style.maxHeight=n};I?(l(),I(),I=null):I=l}}function Ae(t){const a=K.value;if(a){const n=t.getBoundingClientRect().height,l=()=>{document.body.offsetHeight,a.style.maxHeight=`${n}px`,a.style.height=`${Math.max(ee,n)}px`};I?(I(),I=null,l()):I=l}}function Be(){const t=K.value;if(t){t.style.maxHeight="",t.style.height="";const{paneWrapperStyle:a}=e;if(typeof a=="string")t.style.cssText=a;else if(a){const{maxHeight:n,height:l}=a;n!==void 0&&(t.style.maxHeight=n),l!==void 0&&(t.style.height=l)}}}const ue={value:[]},ve=L("next");function ke(t){const a=s.value;let n="next";for(const l of ue.value){if(l===a)break;if(l===t){n="prev";break}}ve.value=n,je(t)}function je(t){const{onActiveNameChange:a,onUpdateValue:n,"onUpdate:value":l}=e;a&&Q(a,t),n&&Q(n,t),l&&Q(l,t),p.value=t}function He(t){const{onClose:a}=e;a&&Q(a,t)}function he(){const{value:t}=T;if(!t)return;const a="transition-disabled";t.classList.add(a),Y(),t.classList.remove(a)}const M=L(null);function te({transitionDisabled:t}){const a=y.value;if(!a)return;t&&a.classList.add("transition-disabled");const n=D();n&&M.value&&(M.value.style.width=`${n.offsetWidth}px`,M.value.style.height=`${n.offsetHeight}px`,M.value.style.transform=`translateX(${n.offsetLeft-At(getComputedStyle(a).paddingLeft)}px)`,t&&M.value.offsetWidth),t&&a.classList.remove("transition-disabled")}oe([s],()=>{e.type==="segment"&&ie(()=>{te({transitionDisabled:!1})})}),Wt(()=>{e.type==="segment"&&te({transitionDisabled:!0})});let ge=0;function Oe(t){var a;if(t.contentRect.width===0&&t.contentRect.height===0||ge===t.contentRect.width)return;ge=t.contentRect.width;const{type:n}=e;if((n==="line"||n==="bar")&&he(),n!=="segment"){const{placement:l}=e;ae((l==="top"||l==="bottom"?(a=S.value)===null||a===void 0?void 0:a.$el:R.value)||null)}}const Ie=le(Oe,64);oe([()=>e.justifyContent,()=>e.size],()=>{ie(()=>{const{type:t}=e;(t==="line"||t==="bar")&&he()})});const N=L(!1);function Fe(t){var a;const{target:n,contentRect:{width:l,height:w}}=t,A=n.parentElement.parentElement.offsetWidth,B=n.parentElement.parentElement.offsetHeight,{placement:X}=e;if(!N.value)X==="top"||X==="bottom"?A<l&&(N.value=!0):B<w&&(N.value=!0);else{const{value:U}=C;if(!U)return;X==="top"||X==="bottom"?A-l>U.$el.offsetWidth&&(N.value=!1):B-w>U.$el.offsetHeight&&(N.value=!1)}ae(((a=S.value)===null||a===void 0?void 0:a.$el)||null)}const De=le(Fe,64);function Me(){const{onAdd:t}=e;t&&t(),ie(()=>{const a=D(),{value:n}=S;!a||!n||n.scrollTo({left:a.offsetLeft,top:0,behavior:"smooth"})})}function ae(t){if(!t)return;const{placement:a}=e;if(a==="top"||a==="bottom"){const{scrollLeft:n,scrollWidth:l,offsetWidth:w}=t;P.value=n<=0,W.value=n+w>=l}else{const{scrollTop:n,scrollHeight:l,offsetHeight:w}=t;P.value=n<=0,W.value=n+w>=l}}const Ne=le(t=>{ae(t.target)},64);It(ce,{triggerRef:H(e,"trigger"),tabStyleRef:H(e,"tabStyle"),tabClassRef:H(e,"tabClass"),addTabStyleRef:H(e,"addTabStyle"),addTabClassRef:H(e,"addTabClass"),paneClassRef:H(e,"paneClass"),paneStyleRef:H(e,"paneStyle"),mergedClsPrefixRef:v,typeRef:H(e,"type"),closableRef:H(e,"closable"),valueRef:s,tabChangeIdRef:_,onBeforeLeaveRef:H(e,"onBeforeLeave"),activateTab:ke,handleClose:He,handleAdd:Me}),_t(()=>{Y(),pe()}),Et(()=>{const{value:t}=E;if(!t)return;const{value:a}=v,n=`${a}-tabs-nav-scroll-wrapper--shadow-start`,l=`${a}-tabs-nav-scroll-wrapper--shadow-end`;P.value?t.classList.remove(n):t.classList.add(n),W.value?t.classList.remove(l):t.classList.add(l)});const Ve={syncBarPosition:()=>{Y()}},Xe=()=>{te({transitionDisabled:!0})},xe=G(()=>{const{value:t}=O,{type:a}=e,n={card:"Card",bar:"Bar",line:"Line",segment:"Segment"}[a],l=`${t}${n}`,{self:{barColor:w,closeIconColor:A,closeIconColorHover:B,closeIconColorPressed:X,tabColor:U,tabBorderColor:Ue,paneTextColor:Ge,tabFontWeight:Ye,tabBorderRadius:Ke,tabFontWeightActive:qe,colorSegment:Je,fontWeightStrong:Qe,tabColorSegment:Ze,closeSize:et,closeIconSize:tt,closeColorHover:at,closeColorPressed:rt,closeBorderRadius:nt,[j("panePadding",t)]:q,[j("tabPadding",l)]:ot,[j("tabPaddingVertical",l)]:it,[j("tabGap",l)]:st,[j("tabGap",`${l}Vertical`)]:lt,[j("tabTextColor",a)]:dt,[j("tabTextColorActive",a)]:bt,[j("tabTextColorHover",a)]:ct,[j("tabTextColorDisabled",a)]:ft,[j("tabFontSize",t)]:pt},common:{cubicBezierEaseInOut:ut}}=m.value;return{"--n-bezier":ut,"--n-color-segment":Je,"--n-bar-color":w,"--n-tab-font-size":pt,"--n-tab-text-color":dt,"--n-tab-text-color-active":bt,"--n-tab-text-color-disabled":ft,"--n-tab-text-color-hover":ct,"--n-pane-text-color":Ge,"--n-tab-border-color":Ue,"--n-tab-border-radius":Ke,"--n-close-size":et,"--n-close-icon-size":tt,"--n-close-color-hover":at,"--n-close-color-pressed":rt,"--n-close-border-radius":nt,"--n-close-icon-color":A,"--n-close-icon-color-hover":B,"--n-close-icon-color-pressed":X,"--n-tab-color":U,"--n-tab-font-weight":Ye,"--n-tab-font-weight-active":qe,"--n-tab-padding":ot,"--n-tab-padding-vertical":it,"--n-tab-gap":st,"--n-tab-gap-vertical":lt,"--n-pane-padding-left":J(q,"left"),"--n-pane-padding-right":J(q,"right"),"--n-pane-padding-top":J(q,"top"),"--n-pane-padding-bottom":J(q,"bottom"),"--n-font-weight-strong":Qe,"--n-tab-color-segment":Ze}}),V=f?Lt("tabs",G(()=>`${O.value[0]}${e.type[0]}`),xe,e):void 0;return Object.assign({mergedClsPrefix:v,mergedValue:s,renderedNames:new Set,segmentCapsuleElRef:M,tabsPaneWrapperRef:K,tabsElRef:y,barElRef:T,addTabInstRef:C,xScrollInstRef:S,scrollWrapperElRef:E,addTabFixed:N,tabWrapperStyle:F,handleNavResize:Ie,mergedSize:O,handleScroll:Ne,handleTabsResize:De,cssVars:f?void 0:xe,themeClass:V==null?void 0:V.themeClass,animationDirection:ve,renderNameListRef:ue,yScrollElRef:R,handleSegmentResize:Xe,onAnimationBeforeLeave:Le,onAnimationEnter:Ae,onAnimationAfterEnter:Be,onRender:V==null?void 0:V.onRender},Ve)},render(){const{mergedClsPrefix:e,type:o,placement:c,addTabFixed:u,addable:d,mergedSize:x,renderNameListRef:v,onRender:f,paneWrapperClass:h,paneWrapperStyle:m,$slots:{default:y,prefix:T,suffix:E}}=this;f==null||f();const C=y?re(y()).filter(p=>p.type.__TAB_PANE__===!0):[],S=y?re(y()).filter(p=>p.type.__TAB__===!0):[],R=!S.length,P=o==="card",W=o==="segment",$=!P&&!W&&this.justifyContent;v.value=[];const O=()=>{const p=b("div",{style:this.tabWrapperStyle,class:`${e}-tabs-wrapper`},$?null:b("div",{class:`${e}-tabs-scroll-padding`,style:c==="top"||c==="bottom"?{width:`${this.tabsPadding}px`}:{height:`${this.tabsPadding}px`}}),R?C.map((s,_)=>(v.value.push(s.props.name),de(b(be,Object.assign({},s.props,{internalCreatedByPane:!0,internalLeftPadded:_!==0&&(!$||$==="center"||$==="start"||$==="end")}),s.children?{default:s.children.tab}:void 0)))):S.map((s,_)=>(v.value.push(s.props.name),de(_!==0&&!$?Te(s):s))),!u&&d&&P?Re(d,(R?C.length:S.length)!==0):null,$?null:b("div",{class:`${e}-tabs-scroll-padding`,style:{width:`${this.tabsPadding}px`}}));return b("div",{ref:"tabsElRef",class:`${e}-tabs-nav-scroll-content`},P&&d?b(ne,{onResize:this.handleTabsResize},{default:()=>p}):p,P?b("div",{class:`${e}-tabs-pad`}):null,P?null:b("div",{ref:"barElRef",class:`${e}-tabs-bar`}))},k=W?"top":c;return b("div",{class:[`${e}-tabs`,this.themeClass,`${e}-tabs--${o}-type`,`${e}-tabs--${x}-size`,$&&`${e}-tabs--flex`,`${e}-tabs--${k}`],style:this.cssVars},b("div",{class:[`${e}-tabs-nav--${o}-type`,`${e}-tabs-nav--${k}`,`${e}-tabs-nav`]},ye(T,p=>p&&b("div",{class:`${e}-tabs-nav__prefix`},p)),W?b(ne,{onResize:this.handleSegmentResize},{default:()=>b("div",{class:`${e}-tabs-rail`,ref:"tabsElRef"},b("div",{class:`${e}-tabs-capsule`,ref:"segmentCapsuleElRef"},b("div",{class:`${e}-tabs-wrapper`},b("div",{class:`${e}-tabs-tab`}))),R?C.map((p,s)=>(v.value.push(p.props.name),b(be,Object.assign({},p.props,{internalCreatedByPane:!0,internalLeftPadded:s!==0}),p.children?{default:p.children.tab}:void 0))):S.map((p,s)=>(v.value.push(p.props.name),s===0?p:Te(p))))}):b(ne,{onResize:this.handleNavResize},{default:()=>b("div",{class:`${e}-tabs-nav-scroll-wrapper`,ref:"scrollWrapperElRef"},["top","bottom"].includes(k)?b(Mt,{ref:"xScrollInstRef",onScroll:this.handleScroll},{default:O}):b("div",{class:`${e}-tabs-nav-y-scroll`,onScroll:this.handleScroll,ref:"yScrollElRef"},O()))}),u&&d&&P?Re(d,!0):null,ye(E,p=>p&&b("div",{class:`${e}-tabs-nav__suffix`},p))),R&&(this.animated&&(k==="top"||k==="bottom")?b("div",{ref:"tabsPaneWrapperRef",style:m,class:[`${e}-tabs-pane-wrapper`,h]},Ce(C,this.mergedValue,this.renderedNames,this.onAnimationBeforeLeave,this.onAnimationEnter,this.onAnimationAfterEnter,this.animationDirection)):Ce(C,this.mergedValue,this.renderedNames)))}});function Ce(e,o,c,u,d,x,v){const f=[];return e.forEach(h=>{const{name:m,displayDirective:y,"display-directive":T}=h.props,E=S=>y===S||T===S,C=o===m;if(h.key!==void 0&&(h.key=m),C||E("show")||E("show:lazy")&&c.has(m)){c.has(m)||c.add(m);const S=!E("if");f.push(S?Bt(h,[[Ot,C]]):h)}}),v?b(kt,{name:`${v}-transition`,onBeforeLeave:u,onEnter:d,onAfterEnter:x},{default:()=>f}):f}function Re(e,o){return b(be,{ref:"addTabInstRef",key:"__addable",name:"__addable",internalCreatedByPane:!0,internalAddable:!0,internalLeftPadded:o,disabled:typeof e=="object"&&e.disabled})}function Te(e){const o=jt(e);return o.props?o.props.internalLeftPadded=!0:o.props={internalLeftPadded:!0},o}function de(e){return Array.isArray(e.dynamicProps)?e.dynamicProps.includes("internalLeftPadded")||e.dynamicProps.push("internalLeftPadded"):e.dynamicProps=["internalLeftPadded"],e}export{ta as N,aa as a};
