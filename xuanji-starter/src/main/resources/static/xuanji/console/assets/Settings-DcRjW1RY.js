import{f as ye,h as C,av as Ht,W as R,aw as Ft,ax as Ke,ay as Gt,az as st,l as Mt,t as qt,o as it,aA as Kt,F as Te,m as Xt,aB as Yt,aC as Qt,v as se,aD as Jt,c as l,a as g,b as E,d as D,e as Zt,g as je,r as Xe,aE as Ae,u as ea,k as dt,aF as ta,aq as $e,a4 as ut,aG as aa,af as oa,q as ra,aH as na,aI as Ye,aJ as la,aK as sa,aL as ia,aM as da,ar as Ve,x as Z,aN as we,aO as ua,p as ba,w as ee,y as Ce,A as le,D as p,G as b,H as i,aP as ca,R as A,T as M,I as Qe,Y as K,z as T,U as fe,Q as S,C as F,P as q,S as re,a7 as Ne,ai as fa,ao as Je,K as ke,au as pa,_ as va}from"./index-FgWOnTD7.js";import{P as ga}from"./PageHero-WvKZEzU4.js";import{g as ha}from"./names-CfFxFgoX.js";import{R as Ze}from"./RefreshOutline-Cs9NdjLG.js";import{P as ya}from"./PlanetOutline-2qNshj96.js";import{A as ma,N as xa}from"./InputNumber-C1HwXNwx.js";import{t as et}from"./toNumber-BPZ0FcD_.js";import{u as wa}from"./use-message-DPdcgKLp.js";import{N as _e}from"./Empty-D34cG5mJ.js";import{N as tt}from"./Space-DKgEX_63.js";import{N as Se}from"./Select-CSQENY7x.js";import{N as Re}from"./Divider-YNezv1n4.js";import{N as pe}from"./Tag-BaDApJwM.js";import{N as ze}from"./Switch-DlzHmEDO.js";import{N as at}from"./Input-B8PSfsSG.js";import{N as Ca}from"./Popconfirm-BK77132i.js";import"./use-locale-CgRSOBYo.js";import"./get-slot-Bk_rJcZu.js";import"./Suffix-mIHTdB6s.js";import"./Checkmark-rVuvcJrV.js";const ka=Ke(".v-x-scroll",{overflow:"auto",scrollbarWidth:"none"},[Ke("&::-webkit-scrollbar",{width:0,height:0})]),_a=ye({name:"XScroll",props:{disabled:Boolean,onScroll:Function},setup(){const t=R(null);function s(h){!(h.currentTarget.offsetWidth<h.currentTarget.scrollWidth)||h.deltaY===0||(h.currentTarget.scrollLeft+=h.deltaY+h.deltaX,h.preventDefault())}const y=Ft();return ka.mount({id:"vueuc/x-scroll",head:!0,anchorMetaName:Ht,ssr:y}),Object.assign({selfRef:t,handleWheel:s},{scrollTo(...h){var O;(O=t.value)===null||O===void 0||O.scrollTo(...h)}})},render(){return C("div",{ref:"selfRef",onScroll:this.onScroll,onWheel:this.disabled?void 0:this.handleWheel,class:"v-x-scroll"},this.$slots)}});var Ue=function(){return Gt.Date.now()},Sa="Expected a function",Ra=Math.max,za=Math.min;function Ta(t,s,y){var _,h,O,v,f,m,z=0,P=!1,I=!1,V=!0;if(typeof t!="function")throw new TypeError(Sa);s=et(s)||0,st(y)&&(P=!!y.leading,I="maxWait"in y,O=I?Ra(et(y.maxWait)||0,s):O,V="trailing"in y?!!y.trailing:V);function B(c){var W=_,te=h;return _=h=void 0,z=c,v=t.apply(te,W),v}function $(c){return z=c,f=setTimeout(U,s),P?B(c):v}function L(c){var W=c-m,te=c-z,ae=s-W;return I?za(ae,O-te):ae}function N(c){var W=c-m,te=c-z;return m===void 0||W>=s||W<0||I&&te>=O}function U(){var c=Ue();if(N(c))return w(c);f=setTimeout(U,L(c))}function w(c){return f=void 0,V&&_?B(c):(_=h=void 0,v)}function X(){f!==void 0&&clearTimeout(f),z=0,_=m=h=f=void 0}function G(){return f===void 0?v:w(Ue())}function k(){var c=Ue(),W=N(c);if(_=arguments,h=this,m=c,W){if(f===void 0)return $(m);if(I)return clearTimeout(f),f=setTimeout(U,s),B(m)}return f===void 0&&(f=setTimeout(U,s)),v}return k.cancel=X,k.flush=G,k}var $a="Expected a function";function Pa(t,s,y){var _=!0,h=!0;if(typeof t!="function")throw new TypeError($a);return st(y)&&(_="leading"in y?!!y.leading:_,h="trailing"in y?!!y.trailing:h),Ta(t,s,{leading:_,maxWait:s,trailing:h})}const Ge=Mt("n-tabs"),bt={tab:[String,Number,Object,Function],name:{type:[String,Number],required:!0},disabled:Boolean,displayDirective:{type:String,default:"if"},closable:{type:Boolean,default:void 0},tabProps:Object,label:[String,Number,Object,Function]},ot=ye({__TAB_PANE__:!0,name:"TabPane",alias:["TabPanel"],props:bt,slots:Object,setup(t){const s=it(Ge,null);return s||qt("tab-pane","`n-tab-pane` must be placed inside `n-tabs`."),{style:s.paneStyleRef,class:s.paneClassRef,mergedClsPrefix:s.mergedClsPrefixRef}},render(){return C("div",{class:[`${this.mergedClsPrefix}-tab-pane`,this.class],style:this.style},this.$slots)}}),Ia=Object.assign({internalLeftPadded:Boolean,internalAddable:Boolean,internalCreatedByPane:Boolean},Jt(bt,["displayDirective"])),Fe=ye({__TAB__:!0,inheritAttrs:!1,name:"Tab",props:Ia,setup(t){const{mergedClsPrefixRef:s,valueRef:y,typeRef:_,closableRef:h,tabStyleRef:O,addTabStyleRef:v,tabClassRef:f,addTabClassRef:m,tabChangeIdRef:z,onBeforeLeaveRef:P,triggerRef:I,handleAdd:V,activateTab:B,handleClose:$}=it(Ge);return{trigger:I,mergedClosable:se(()=>{if(t.internalAddable)return!1;const{closable:L}=t;return L===void 0?h.value:L}),style:O,addStyle:v,tabClass:f,addTabClass:m,clsPrefix:s,value:y,type:_,handleClose(L){L.stopPropagation(),!t.disabled&&$(t.name)},activateTab(){if(t.disabled)return;if(t.internalAddable){V();return}const{name:L}=t,N=++z.id;if(L!==y.value){const{value:U}=P;U?Promise.resolve(U(t.name,y.value)).then(w=>{w&&z.id===N&&B(L)}):B(L)}}}},render(){const{internalAddable:t,clsPrefix:s,name:y,disabled:_,label:h,tab:O,value:v,mergedClosable:f,trigger:m,$slots:{default:z}}=this,P=h??O;return C("div",{class:`${s}-tabs-tab-wrapper`},this.internalLeftPadded?C("div",{class:`${s}-tabs-tab-pad`}):null,C("div",Object.assign({key:y,"data-name":y,"data-disabled":_?!0:void 0},Kt({class:[`${s}-tabs-tab`,v===y&&`${s}-tabs-tab--active`,_&&`${s}-tabs-tab--disabled`,f&&`${s}-tabs-tab--closable`,t&&`${s}-tabs-tab--addable`,t?this.addTabClass:this.tabClass],onClick:m==="click"?this.activateTab:void 0,onMouseenter:m==="hover"?this.activateTab:void 0,style:t?this.addStyle:this.style},this.internalCreatedByPane?this.tabProps||{}:this.$attrs)),C("span",{class:`${s}-tabs-tab__label`},t?C(Te,null,C("div",{class:`${s}-tabs-tab__height-placeholder`}," "),C(Xt,{clsPrefix:s},{default:()=>C(ma,null)})):z?z():typeof P=="object"?P:Yt(P??y)),f&&this.type==="card"?C(Qt,{clsPrefix:s,class:`${s}-tabs-tab__close`,onClick:this.handleClose,disabled:_}):null))}}),Oa=l("tabs",`
 box-sizing: border-box;
 width: 100%;
 display: flex;
 flex-direction: column;
 transition:
 background-color .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
`,[g("segment-type",[l("tabs-rail",[E("&.transition-disabled",[l("tabs-capsule",`
 transition: none;
 `)])])]),g("top",[l("tab-pane",`
 padding: var(--n-pane-padding-top) var(--n-pane-padding-right) var(--n-pane-padding-bottom) var(--n-pane-padding-left);
 `)]),g("left",[l("tab-pane",`
 padding: var(--n-pane-padding-right) var(--n-pane-padding-bottom) var(--n-pane-padding-left) var(--n-pane-padding-top);
 `)]),g("left, right",`
 flex-direction: row;
 `,[l("tabs-bar",`
 width: 2px;
 right: 0;
 transition:
 top .2s var(--n-bezier),
 max-height .2s var(--n-bezier),
 background-color .3s var(--n-bezier);
 `),l("tabs-tab",`
 padding: var(--n-tab-padding-vertical); 
 `)]),g("right",`
 flex-direction: row-reverse;
 `,[l("tab-pane",`
 padding: var(--n-pane-padding-left) var(--n-pane-padding-top) var(--n-pane-padding-right) var(--n-pane-padding-bottom);
 `),l("tabs-bar",`
 left: 0;
 `)]),g("bottom",`
 flex-direction: column-reverse;
 justify-content: flex-end;
 `,[l("tab-pane",`
 padding: var(--n-pane-padding-bottom) var(--n-pane-padding-right) var(--n-pane-padding-top) var(--n-pane-padding-left);
 `),l("tabs-bar",`
 top: 0;
 `)]),l("tabs-rail",`
 position: relative;
 padding: 3px;
 border-radius: var(--n-tab-border-radius);
 width: 100%;
 background-color: var(--n-color-segment);
 transition: background-color .3s var(--n-bezier);
 display: flex;
 align-items: center;
 `,[l("tabs-capsule",`
 border-radius: var(--n-tab-border-radius);
 position: absolute;
 pointer-events: none;
 background-color: var(--n-tab-color-segment);
 box-shadow: 0 1px 3px 0 rgba(0, 0, 0, .08);
 transition: transform 0.3s var(--n-bezier);
 `),l("tabs-tab-wrapper",`
 flex-basis: 0;
 flex-grow: 1;
 display: flex;
 align-items: center;
 justify-content: center;
 `,[l("tabs-tab",`
 overflow: hidden;
 border-radius: var(--n-tab-border-radius);
 width: 100%;
 display: flex;
 align-items: center;
 justify-content: center;
 `,[g("active",`
 font-weight: var(--n-font-weight-strong);
 color: var(--n-tab-text-color-active);
 `),E("&:hover",`
 color: var(--n-tab-text-color-hover);
 `)])])]),g("flex",[l("tabs-nav",`
 width: 100%;
 position: relative;
 `,[l("tabs-wrapper",`
 width: 100%;
 `,[l("tabs-tab",`
 margin-right: 0;
 `)])])]),l("tabs-nav",`
 box-sizing: border-box;
 line-height: 1.5;
 display: flex;
 transition: border-color .3s var(--n-bezier);
 `,[D("prefix, suffix",`
 display: flex;
 align-items: center;
 `),D("prefix","padding-right: 16px;"),D("suffix","padding-left: 16px;")]),g("top, bottom",[E(">",[l("tabs-nav",[l("tabs-nav-scroll-wrapper",[E("&::before",`
 top: 0;
 bottom: 0;
 left: 0;
 width: 20px;
 `),E("&::after",`
 top: 0;
 bottom: 0;
 right: 0;
 width: 20px;
 `),g("shadow-start",[E("&::before",`
 box-shadow: inset 10px 0 8px -8px rgba(0, 0, 0, .12);
 `)]),g("shadow-end",[E("&::after",`
 box-shadow: inset -10px 0 8px -8px rgba(0, 0, 0, .12);
 `)])])])])]),g("left, right",[l("tabs-nav-scroll-content",`
 flex-direction: column;
 `),E(">",[l("tabs-nav",[l("tabs-nav-scroll-wrapper",[E("&::before",`
 top: 0;
 left: 0;
 right: 0;
 height: 20px;
 `),E("&::after",`
 bottom: 0;
 left: 0;
 right: 0;
 height: 20px;
 `),g("shadow-start",[E("&::before",`
 box-shadow: inset 0 10px 8px -8px rgba(0, 0, 0, .12);
 `)]),g("shadow-end",[E("&::after",`
 box-shadow: inset 0 -10px 8px -8px rgba(0, 0, 0, .12);
 `)])])])])]),l("tabs-nav-scroll-wrapper",`
 flex: 1;
 position: relative;
 overflow: hidden;
 `,[l("tabs-nav-y-scroll",`
 height: 100%;
 width: 100%;
 overflow-y: auto; 
 scrollbar-width: none;
 `,[E("&::-webkit-scrollbar, &::-webkit-scrollbar-track-piece, &::-webkit-scrollbar-thumb",`
 width: 0;
 height: 0;
 display: none;
 `)]),E("&::before, &::after",`
 transition: box-shadow .3s var(--n-bezier);
 pointer-events: none;
 content: "";
 position: absolute;
 z-index: 1;
 `)]),l("tabs-nav-scroll-content",`
 display: flex;
 position: relative;
 min-width: 100%;
 min-height: 100%;
 width: fit-content;
 box-sizing: border-box;
 `),l("tabs-wrapper",`
 display: inline-flex;
 flex-wrap: nowrap;
 position: relative;
 `),l("tabs-tab-wrapper",`
 display: flex;
 flex-wrap: nowrap;
 flex-shrink: 0;
 flex-grow: 0;
 `),l("tabs-tab",`
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
 `,[g("disabled",{cursor:"not-allowed"}),D("close",`
 margin-left: 6px;
 transition:
 background-color .3s var(--n-bezier),
 color .3s var(--n-bezier);
 `),D("label",`
 display: flex;
 align-items: center;
 z-index: 1;
 `)]),l("tabs-bar",`
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
 `,[E("&.transition-disabled",`
 transition: none;
 `),g("disabled",`
 background-color: var(--n-tab-text-color-disabled)
 `)]),l("tabs-pane-wrapper",`
 position: relative;
 overflow: hidden;
 transition: max-height .2s var(--n-bezier);
 `),l("tab-pane",`
 color: var(--n-pane-text-color);
 width: 100%;
 transition:
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier),
 opacity .2s var(--n-bezier);
 left: 0;
 right: 0;
 top: 0;
 `,[E("&.next-transition-leave-active, &.prev-transition-leave-active, &.next-transition-enter-active, &.prev-transition-enter-active",`
 transition:
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier),
 transform .2s var(--n-bezier),
 opacity .2s var(--n-bezier);
 `),E("&.next-transition-leave-active, &.prev-transition-leave-active",`
 position: absolute;
 `),E("&.next-transition-enter-from, &.prev-transition-leave-to",`
 transform: translateX(32px);
 opacity: 0;
 `),E("&.next-transition-leave-to, &.prev-transition-enter-from",`
 transform: translateX(-32px);
 opacity: 0;
 `),E("&.next-transition-leave-from, &.next-transition-enter-to, &.prev-transition-leave-from, &.prev-transition-enter-to",`
 transform: translateX(0);
 opacity: 1;
 `)]),l("tabs-tab-pad",`
 box-sizing: border-box;
 width: var(--n-tab-gap);
 flex-grow: 0;
 flex-shrink: 0;
 `),g("line-type, bar-type",[l("tabs-tab",`
 font-weight: var(--n-tab-font-weight);
 box-sizing: border-box;
 vertical-align: bottom;
 `,[E("&:hover",{color:"var(--n-tab-text-color-hover)"}),g("active",`
 color: var(--n-tab-text-color-active);
 font-weight: var(--n-tab-font-weight-active);
 `),g("disabled",{color:"var(--n-tab-text-color-disabled)"})])]),l("tabs-nav",[g("line-type",[g("top",[D("prefix, suffix",`
 border-bottom: 1px solid var(--n-tab-border-color);
 `),l("tabs-nav-scroll-content",`
 border-bottom: 1px solid var(--n-tab-border-color);
 `),l("tabs-bar",`
 bottom: -1px;
 `)]),g("left",[D("prefix, suffix",`
 border-right: 1px solid var(--n-tab-border-color);
 `),l("tabs-nav-scroll-content",`
 border-right: 1px solid var(--n-tab-border-color);
 `),l("tabs-bar",`
 right: -1px;
 `)]),g("right",[D("prefix, suffix",`
 border-left: 1px solid var(--n-tab-border-color);
 `),l("tabs-nav-scroll-content",`
 border-left: 1px solid var(--n-tab-border-color);
 `),l("tabs-bar",`
 left: -1px;
 `)]),g("bottom",[D("prefix, suffix",`
 border-top: 1px solid var(--n-tab-border-color);
 `),l("tabs-nav-scroll-content",`
 border-top: 1px solid var(--n-tab-border-color);
 `),l("tabs-bar",`
 top: -1px;
 `)]),D("prefix, suffix",`
 transition: border-color .3s var(--n-bezier);
 `),l("tabs-nav-scroll-content",`
 transition: border-color .3s var(--n-bezier);
 `),l("tabs-bar",`
 border-radius: 0;
 `)]),g("card-type",[D("prefix, suffix",`
 transition: border-color .3s var(--n-bezier);
 `),l("tabs-pad",`
 flex-grow: 1;
 transition: border-color .3s var(--n-bezier);
 `),l("tabs-tab-pad",`
 transition: border-color .3s var(--n-bezier);
 `),l("tabs-tab",`
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
 `,[g("addable",`
 padding-left: 8px;
 padding-right: 8px;
 font-size: 16px;
 justify-content: center;
 `,[D("height-placeholder",`
 width: 0;
 font-size: var(--n-tab-font-size);
 `),Zt("disabled",[E("&:hover",`
 color: var(--n-tab-text-color-hover);
 `)])]),g("closable","padding-right: 8px;"),g("active",`
 background-color: #0000;
 font-weight: var(--n-tab-font-weight-active);
 color: var(--n-tab-text-color-active);
 `),g("disabled","color: var(--n-tab-text-color-disabled);")])]),g("left, right",`
 flex-direction: column; 
 `,[D("prefix, suffix",`
 padding: var(--n-tab-padding-vertical);
 `),l("tabs-wrapper",`
 flex-direction: column;
 `),l("tabs-tab-wrapper",`
 flex-direction: column;
 `,[l("tabs-tab-pad",`
 height: var(--n-tab-gap-vertical);
 width: 100%;
 `)])]),g("top",[g("card-type",[l("tabs-scroll-padding","border-bottom: 1px solid var(--n-tab-border-color);"),D("prefix, suffix",`
 border-bottom: 1px solid var(--n-tab-border-color);
 `),l("tabs-tab",`
 border-top-left-radius: var(--n-tab-border-radius);
 border-top-right-radius: var(--n-tab-border-radius);
 `,[g("active",`
 border-bottom: 1px solid #0000;
 `)]),l("tabs-tab-pad",`
 border-bottom: 1px solid var(--n-tab-border-color);
 `),l("tabs-pad",`
 border-bottom: 1px solid var(--n-tab-border-color);
 `)])]),g("left",[g("card-type",[l("tabs-scroll-padding","border-right: 1px solid var(--n-tab-border-color);"),D("prefix, suffix",`
 border-right: 1px solid var(--n-tab-border-color);
 `),l("tabs-tab",`
 border-top-left-radius: var(--n-tab-border-radius);
 border-bottom-left-radius: var(--n-tab-border-radius);
 `,[g("active",`
 border-right: 1px solid #0000;
 `)]),l("tabs-tab-pad",`
 border-right: 1px solid var(--n-tab-border-color);
 `),l("tabs-pad",`
 border-right: 1px solid var(--n-tab-border-color);
 `)])]),g("right",[g("card-type",[l("tabs-scroll-padding","border-left: 1px solid var(--n-tab-border-color);"),D("prefix, suffix",`
 border-left: 1px solid var(--n-tab-border-color);
 `),l("tabs-tab",`
 border-top-right-radius: var(--n-tab-border-radius);
 border-bottom-right-radius: var(--n-tab-border-radius);
 `,[g("active",`
 border-left: 1px solid #0000;
 `)]),l("tabs-tab-pad",`
 border-left: 1px solid var(--n-tab-border-color);
 `),l("tabs-pad",`
 border-left: 1px solid var(--n-tab-border-color);
 `)])]),g("bottom",[g("card-type",[l("tabs-scroll-padding","border-top: 1px solid var(--n-tab-border-color);"),D("prefix, suffix",`
 border-top: 1px solid var(--n-tab-border-color);
 `),l("tabs-tab",`
 border-bottom-left-radius: var(--n-tab-border-radius);
 border-bottom-right-radius: var(--n-tab-border-radius);
 `,[g("active",`
 border-top: 1px solid #0000;
 `)]),l("tabs-tab-pad",`
 border-top: 1px solid var(--n-tab-border-color);
 `),l("tabs-pad",`
 border-top: 1px solid var(--n-tab-border-color);
 `)])])])]),De=Pa,Wa=Object.assign(Object.assign({},dt.props),{value:[String,Number],defaultValue:[String,Number],trigger:{type:String,default:"click"},type:{type:String,default:"bar"},closable:Boolean,justifyContent:String,size:String,placement:{type:String,default:"top"},tabStyle:[String,Object],tabClass:String,addTabStyle:[String,Object],addTabClass:String,barWidth:Number,paneClass:String,paneStyle:[String,Object],paneWrapperClass:String,paneWrapperStyle:[String,Object],addable:[Boolean,Object],tabsPadding:{type:Number,default:0},animated:Boolean,onBeforeLeave:Function,onAdd:Function,"onUpdate:value":[Function,Array],onUpdateValue:[Function,Array],onClose:[Function,Array],labelSize:String,activeName:[String,Number],onActiveNameChange:[Function,Array]}),Ba=ye({name:"Tabs",props:Wa,slots:Object,setup(t,{slots:s}){var y,_,h,O;const{mergedClsPrefixRef:v,inlineThemeDisabled:f,mergedComponentPropsRef:m}=ea(t),z=dt("Tabs","-tabs",Oa,na,t,v),P=R(null),I=R(null),V=R(null),B=R(null),$=R(null),L=R(null),N=R(!0),U=R(!0),w=Ye(t,["labelSize","size"]),X=se(()=>{var a,o;if(w.value)return w.value;const d=(o=(a=m==null?void 0:m.value)===null||a===void 0?void 0:a.Tabs)===null||o===void 0?void 0:o.size;return d||"medium"}),G=Ye(t,["activeName","value"]),k=R((_=(y=G.value)!==null&&y!==void 0?y:t.defaultValue)!==null&&_!==void 0?_:s.default?(O=(h=je(s.default())[0])===null||h===void 0?void 0:h.props)===null||O===void 0?void 0:O.name:null),c=ta(G,k),W={id:0},te=se(()=>{if(!(!t.justifyContent||t.type==="card"))return{display:"flex",justifyContent:t.justifyContent}});$e(c,()=>{W.id=0,ne(),ve()});function ae(){var a;const{value:o}=c;return o===null?null:(a=P.value)===null||a===void 0?void 0:a.querySelector(`[data-name="${o}"]`)}function Pe(a){if(t.type==="card")return;const{value:o}=I;if(!o)return;const d=o.style.opacity==="0";if(a){const x=`${v.value}-tabs-bar--disabled`,{barWidth:j,placement:Y}=t;if(a.dataset.disabled==="true"?o.classList.add(x):o.classList.remove(x),["top","bottom"].includes(Y)){if(me(["top","maxHeight","height"]),typeof j=="number"&&a.offsetWidth>=j){const Q=Math.floor((a.offsetWidth-j)/2)+a.offsetLeft;o.style.left=`${Q}px`,o.style.maxWidth=`${j}px`}else o.style.left=`${a.offsetLeft}px`,o.style.maxWidth=`${a.offsetWidth}px`;o.style.width="8192px",d&&(o.style.transition="none"),o.offsetWidth,d&&(o.style.transition="",o.style.opacity="1")}else{if(me(["left","maxWidth","width"]),typeof j=="number"&&a.offsetHeight>=j){const Q=Math.floor((a.offsetHeight-j)/2)+a.offsetTop;o.style.top=`${Q}px`,o.style.maxHeight=`${j}px`}else o.style.top=`${a.offsetTop}px`,o.style.maxHeight=`${a.offsetHeight}px`;o.style.height="8192px",d&&(o.style.transition="none"),o.offsetHeight,d&&(o.style.transition="",o.style.opacity="1")}}}function Ie(){if(t.type==="card")return;const{value:a}=I;a&&(a.style.opacity="0")}function me(a){const{value:o}=I;if(o)for(const d of a)o.style[d]=""}function ne(){if(t.type==="card")return;const a=ae();a?Pe(a):Ie()}function ve(){var a;const o=(a=$.value)===null||a===void 0?void 0:a.$el;if(!o)return;const d=ae();if(!d)return;const{scrollLeft:x,offsetWidth:j}=o,{offsetLeft:Y,offsetWidth:Q}=d;x>Y?o.scrollTo({top:0,left:Y,behavior:"smooth"}):Y+Q>x+j&&o.scrollTo({top:0,left:Y+Q-j,behavior:"smooth"})}const oe=R(null);let ge=0,J=null;function Oe(a){const o=oe.value;if(o){ge=a.getBoundingClientRect().height;const d=`${ge}px`,x=()=>{o.style.height=d,o.style.maxHeight=d};J?(x(),J(),J=null):J=x}}function We(a){const o=oe.value;if(o){const d=a.getBoundingClientRect().height,x=()=>{document.body.offsetHeight,o.style.maxHeight=`${d}px`,o.style.height=`${Math.max(ge,d)}px`};J?(J(),J=null,x()):J=x}}function Be(){const a=oe.value;if(a){a.style.maxHeight="",a.style.height="";const{paneWrapperStyle:o}=t;if(typeof o=="string")a.style.cssText=o;else if(o){const{maxHeight:d,height:x}=o;d!==void 0&&(a.style.maxHeight=d),x!==void 0&&(a.style.height=x)}}}const r={value:[]},e=R("next");function n(a){const o=c.value;let d="next";for(const x of r.value){if(x===o)break;if(x===a){d="prev";break}}e.value=d,u(a)}function u(a){const{onActiveNameChange:o,onUpdateValue:d,"onUpdate:value":x}=t;o&&Ce(o,a),d&&Ce(d,a),x&&Ce(x,a),k.value=a}function H(a){const{onClose:o}=t;o&&Ce(o,a)}function ie(){const{value:a}=I;if(!a)return;const o="transition-disabled";a.classList.add(o),ne(),a.classList.remove(o)}const de=R(null);function Ee({transitionDisabled:a}){const o=P.value;if(!o)return;a&&o.classList.add("transition-disabled");const d=ae();d&&de.value&&(de.value.style.width=`${d.offsetWidth}px`,de.value.style.height=`${d.offsetHeight}px`,de.value.style.transform=`translateX(${d.offsetLeft-la(getComputedStyle(o).paddingLeft)}px)`,a&&de.value.offsetWidth),a&&o.classList.remove("transition-disabled")}$e([c],()=>{t.type==="segment"&&Ve(()=>{Ee({transitionDisabled:!1})})}),ut(()=>{t.type==="segment"&&Ee({transitionDisabled:!0})});let Me=0;function ct(a){var o;if(a.contentRect.width===0&&a.contentRect.height===0||Me===a.contentRect.width)return;Me=a.contentRect.width;const{type:d}=t;if((d==="line"||d==="bar")&&ie(),d!=="segment"){const{placement:x}=t;Le((x==="top"||x==="bottom"?(o=$.value)===null||o===void 0?void 0:o.$el:L.value)||null)}}const ft=De(ct,64);$e([()=>t.justifyContent,()=>t.size],()=>{Ve(()=>{const{type:a}=t;(a==="line"||a==="bar")&&ie()})});const ue=R(!1);function pt(a){var o;const{target:d,contentRect:{width:x,height:j}}=a,Y=d.parentElement.parentElement.offsetWidth,Q=d.parentElement.parentElement.offsetHeight,{placement:ce}=t;if(!ue.value)ce==="top"||ce==="bottom"?Y<x&&(ue.value=!0):Q<j&&(ue.value=!0);else{const{value:he}=B;if(!he)return;ce==="top"||ce==="bottom"?Y-x>he.$el.offsetWidth&&(ue.value=!1):Q-j>he.$el.offsetHeight&&(ue.value=!1)}Le(((o=$.value)===null||o===void 0?void 0:o.$el)||null)}const vt=De(pt,64);function gt(){const{onAdd:a}=t;a&&a(),Ve(()=>{const o=ae(),{value:d}=$;!o||!d||d.scrollTo({left:o.offsetLeft,top:0,behavior:"smooth"})})}function Le(a){if(!a)return;const{placement:o}=t;if(o==="top"||o==="bottom"){const{scrollLeft:d,scrollWidth:x,offsetWidth:j}=a;N.value=d<=0,U.value=d+j>=x}else{const{scrollTop:d,scrollHeight:x,offsetHeight:j}=a;N.value=d<=0,U.value=d+j>=x}}const ht=De(a=>{Le(a.target)},64);ba(Ge,{triggerRef:ee(t,"trigger"),tabStyleRef:ee(t,"tabStyle"),tabClassRef:ee(t,"tabClass"),addTabStyleRef:ee(t,"addTabStyle"),addTabClassRef:ee(t,"addTabClass"),paneClassRef:ee(t,"paneClass"),paneStyleRef:ee(t,"paneStyle"),mergedClsPrefixRef:v,typeRef:ee(t,"type"),closableRef:ee(t,"closable"),valueRef:c,tabChangeIdRef:W,onBeforeLeaveRef:ee(t,"onBeforeLeave"),activateTab:n,handleClose:H,handleAdd:gt}),aa(()=>{ne(),ve()}),oa(()=>{const{value:a}=V;if(!a)return;const{value:o}=v,d=`${o}-tabs-nav-scroll-wrapper--shadow-start`,x=`${o}-tabs-nav-scroll-wrapper--shadow-end`;N.value?a.classList.remove(d):a.classList.add(d),U.value?a.classList.remove(x):a.classList.add(x)});const yt={syncBarPosition:()=>{ne()}},mt=()=>{Ee({transitionDisabled:!0})},qe=se(()=>{const{value:a}=X,{type:o}=t,d={card:"Card",bar:"Bar",line:"Line",segment:"Segment"}[o],x=`${a}${d}`,{self:{barColor:j,closeIconColor:Y,closeIconColorHover:Q,closeIconColorPressed:ce,tabColor:he,tabBorderColor:xt,paneTextColor:wt,tabFontWeight:Ct,tabBorderRadius:kt,tabFontWeightActive:_t,colorSegment:St,fontWeightStrong:Rt,tabColorSegment:zt,closeSize:Tt,closeIconSize:$t,closeColorHover:Pt,closeColorPressed:It,closeBorderRadius:Ot,[Z("panePadding",a)]:xe,[Z("tabPadding",x)]:Wt,[Z("tabPaddingVertical",x)]:Bt,[Z("tabGap",x)]:Et,[Z("tabGap",`${x}Vertical`)]:Lt,[Z("tabTextColor",o)]:jt,[Z("tabTextColorActive",o)]:At,[Z("tabTextColorHover",o)]:Vt,[Z("tabTextColorDisabled",o)]:Nt,[Z("tabFontSize",a)]:Ut},common:{cubicBezierEaseInOut:Dt}}=z.value;return{"--n-bezier":Dt,"--n-color-segment":St,"--n-bar-color":j,"--n-tab-font-size":Ut,"--n-tab-text-color":jt,"--n-tab-text-color-active":At,"--n-tab-text-color-disabled":Nt,"--n-tab-text-color-hover":Vt,"--n-pane-text-color":wt,"--n-tab-border-color":xt,"--n-tab-border-radius":kt,"--n-close-size":Tt,"--n-close-icon-size":$t,"--n-close-color-hover":Pt,"--n-close-color-pressed":It,"--n-close-border-radius":Ot,"--n-close-icon-color":Y,"--n-close-icon-color-hover":Q,"--n-close-icon-color-pressed":ce,"--n-tab-color":he,"--n-tab-font-weight":Ct,"--n-tab-font-weight-active":_t,"--n-tab-padding":Wt,"--n-tab-padding-vertical":Bt,"--n-tab-gap":Et,"--n-tab-gap-vertical":Lt,"--n-pane-padding-left":we(xe,"left"),"--n-pane-padding-right":we(xe,"right"),"--n-pane-padding-top":we(xe,"top"),"--n-pane-padding-bottom":we(xe,"bottom"),"--n-font-weight-strong":Rt,"--n-tab-color-segment":zt}}),be=f?ra("tabs",se(()=>`${X.value[0]}${t.type[0]}`),qe,t):void 0;return Object.assign({mergedClsPrefix:v,mergedValue:c,renderedNames:new Set,segmentCapsuleElRef:de,tabsPaneWrapperRef:oe,tabsElRef:P,barElRef:I,addTabInstRef:B,xScrollInstRef:$,scrollWrapperElRef:V,addTabFixed:ue,tabWrapperStyle:te,handleNavResize:ft,mergedSize:X,handleScroll:ht,handleTabsResize:vt,cssVars:f?void 0:qe,themeClass:be==null?void 0:be.themeClass,animationDirection:e,renderNameListRef:r,yScrollElRef:L,handleSegmentResize:mt,onAnimationBeforeLeave:Oe,onAnimationEnter:We,onAnimationAfterEnter:Be,onRender:be==null?void 0:be.onRender},yt)},render(){const{mergedClsPrefix:t,type:s,placement:y,addTabFixed:_,addable:h,mergedSize:O,renderNameListRef:v,onRender:f,paneWrapperClass:m,paneWrapperStyle:z,$slots:{default:P,prefix:I,suffix:V}}=this;f==null||f();const B=P?je(P()).filter(k=>k.type.__TAB_PANE__===!0):[],$=P?je(P()).filter(k=>k.type.__TAB__===!0):[],L=!$.length,N=s==="card",U=s==="segment",w=!N&&!U&&this.justifyContent;v.value=[];const X=()=>{const k=C("div",{style:this.tabWrapperStyle,class:`${t}-tabs-wrapper`},w?null:C("div",{class:`${t}-tabs-scroll-padding`,style:y==="top"||y==="bottom"?{width:`${this.tabsPadding}px`}:{height:`${this.tabsPadding}px`}}),L?B.map((c,W)=>(v.value.push(c.props.name),He(C(Fe,Object.assign({},c.props,{internalCreatedByPane:!0,internalLeftPadded:W!==0&&(!w||w==="center"||w==="start"||w==="end")}),c.children?{default:c.children.tab}:void 0)))):$.map((c,W)=>(v.value.push(c.props.name),He(W!==0&&!w?lt(c):c))),!_&&h&&N?nt(h,(L?B.length:$.length)!==0):null,w?null:C("div",{class:`${t}-tabs-scroll-padding`,style:{width:`${this.tabsPadding}px`}}));return C("div",{ref:"tabsElRef",class:`${t}-tabs-nav-scroll-content`},N&&h?C(Ae,{onResize:this.handleTabsResize},{default:()=>k}):k,N?C("div",{class:`${t}-tabs-pad`}):null,N?null:C("div",{ref:"barElRef",class:`${t}-tabs-bar`}))},G=U?"top":y;return C("div",{class:[`${t}-tabs`,this.themeClass,`${t}-tabs--${s}-type`,`${t}-tabs--${O}-size`,w&&`${t}-tabs--flex`,`${t}-tabs--${G}`],style:this.cssVars},C("div",{class:[`${t}-tabs-nav--${s}-type`,`${t}-tabs-nav--${G}`,`${t}-tabs-nav`]},Xe(I,k=>k&&C("div",{class:`${t}-tabs-nav__prefix`},k)),U?C(Ae,{onResize:this.handleSegmentResize},{default:()=>C("div",{class:`${t}-tabs-rail`,ref:"tabsElRef"},C("div",{class:`${t}-tabs-capsule`,ref:"segmentCapsuleElRef"},C("div",{class:`${t}-tabs-wrapper`},C("div",{class:`${t}-tabs-tab`}))),L?B.map((k,c)=>(v.value.push(k.props.name),C(Fe,Object.assign({},k.props,{internalCreatedByPane:!0,internalLeftPadded:c!==0}),k.children?{default:k.children.tab}:void 0))):$.map((k,c)=>(v.value.push(k.props.name),c===0?k:lt(k))))}):C(Ae,{onResize:this.handleNavResize},{default:()=>C("div",{class:`${t}-tabs-nav-scroll-wrapper`,ref:"scrollWrapperElRef"},["top","bottom"].includes(G)?C(_a,{ref:"xScrollInstRef",onScroll:this.handleScroll},{default:X}):C("div",{class:`${t}-tabs-nav-y-scroll`,onScroll:this.handleScroll,ref:"yScrollElRef"},X()))}),_&&h&&N?nt(h,!0):null,Xe(V,k=>k&&C("div",{class:`${t}-tabs-nav__suffix`},k))),L&&(this.animated&&(G==="top"||G==="bottom")?C("div",{ref:"tabsPaneWrapperRef",style:z,class:[`${t}-tabs-pane-wrapper`,m]},rt(B,this.mergedValue,this.renderedNames,this.onAnimationBeforeLeave,this.onAnimationEnter,this.onAnimationAfterEnter,this.animationDirection)):rt(B,this.mergedValue,this.renderedNames)))}});function rt(t,s,y,_,h,O,v){const f=[];return t.forEach(m=>{const{name:z,displayDirective:P,"display-directive":I}=m.props,V=$=>P===$||I===$,B=s===z;if(m.key!==void 0&&(m.key=z),B||V("show")||V("show:lazy")&&y.has(z)){y.has(z)||y.add(z);const $=!V("if");f.push($?sa(m,[[ua,B]]):m)}}),v?C(ia,{name:`${v}-transition`,onBeforeLeave:_,onEnter:h,onAfterEnter:O},{default:()=>f}):f}function nt(t,s){return C(Fe,{ref:"addTabInstRef",key:"__addable",name:"__addable",internalCreatedByPane:!0,internalAddable:!0,internalLeftPadded:s,disabled:typeof t=="object"&&t.disabled})}function lt(t){const s=da(t);return s.props?s.props.internalLeftPadded=!0:s.props={internalLeftPadded:!0},s}function He(t){return Array.isArray(t.dynamicProps)?t.dynamicProps.includes("internalLeftPadded")||t.dynamicProps.push("internalLeftPadded"):t.dynamicProps=["internalLeftPadded"],t}const Ea={class:"tab-label"},La={class:"tab-pane-inner"},ja={key:1,class:"config-list"},Aa={class:"config-label"},Va={class:"config-field"},Na={class:"config-actions"},Ua={class:"tab-label"},Da={class:"tab-pane-inner"},Ha={class:"ignore-global"},Fa={class:"ignore-bot-header"},Ga={class:"ignore-groups"},Ma={class:"ignore-group-info"},qa=ye({__name:"Settings",setup(t){const s=wa(),y=[{key:"ignore_bot_messages",label:"忽略其他机器人消息",type:"switch",defaultValue:!1,scope:["global","bot","group"],hint:"全局开关；bot/群 级可单独覆盖；true=收到机器人消息只记录不处理"},{key:"command.prefix",label:"命令前缀",type:"text",defaultValue:"",scope:["global"],hint:"群/私聊命令的前缀。留空 = 不裁剪前缀"},{key:"framework.rate_limit.enabled",label:"启用命令限速",type:"switch",defaultValue:!1,scope:["global","bot","group"],hint:"同一用户在这个时间窗口内只能触发一次命令（开启后生效）"},{key:"framework.rate_limit.window_ms",label:"限速窗口时长（ms）",type:"number",defaultValue:2e3,scope:["global","bot","group"],hint:"限速窗口毫秒；默认 2000ms"},{key:"console.refresh_interval_ms",label:"监控页自动刷新间隔（ms）",type:"number",defaultValue:5e3,scope:["global"],hint:"健康/仪表盘页面自动刷新间隔，默认 5000"},{key:"media.download.enabled",label:"启用媒体按需下载",type:"switch",defaultValue:!1,scope:["global"],hint:"框架层在收到图片/语音时按需下载到本地（按内容去重，TTL+配额清理）"},{key:"media.download.max_file_bytes",label:"媒体单文件上限（字节）",type:"number",defaultValue:209715200,scope:["global"],hint:"单文件超过此大小跳过；默认 200MB"},{key:"media.storage.ttl_days",label:"媒体保留天数",type:"number",defaultValue:7,scope:["global"],hint:"下载的媒体文件保留天数；过期自动删除"},{key:"media.storage.max_bytes",label:"媒体总配额（字节）",type:"number",defaultValue:4294967296,scope:["global"],hint:"所有媒体总占用；超限自动删最旧；默认 4GB"},{key:"framework.qqbot.api_base_mode",label:"QQ 开放平台 API 基地址",type:"select",defaultValue:"new",scope:["global"],hint:"new=新统一地址 api.bot.qq.com（不区分沙箱/正式，推荐）；legacy=老平台 api.sgroup.qq.com，按机器人环境自动选正式/沙箱。改后 30 秒内生效（WebSocket 连接需重启机器人）",options:[{label:"新统一地址（api.bot.qq.com）",value:"new"},{label:"老平台（正式/沙箱自动区分）",value:"legacy"}]},{key:"command_prefix",label:"机器人命令前缀",type:"text",defaultValue:"",scope:["bot","group"],hint:"该机器人/群专属命令前缀（覆盖全局）"},{key:"rate_limit_enabled",label:"启用命令限速（本机器人）",type:"switch",defaultValue:!1,scope:["bot","group"],hint:"该机器人/群是否启用命令限速（覆盖全局）"},{key:"rate_limit_window_ms",label:"限速窗口（ms）",type:"number",defaultValue:2e3,scope:["bot","group"],hint:"该机器人/群专属限速窗口"},{key:"welcome_enabled",label:"入群欢迎语",type:"switch",defaultValue:!1,scope:["bot","group"],hint:"机器人被加入该群时自动发送欢迎语"},{key:"welcome_message",label:"欢迎语内容",type:"longtext",defaultValue:"",scope:["bot","group"],hint:"支持多行文本，可包含 @变量"},{key:"cb_threshold",label:"熔断阈值",type:"number",defaultValue:5,scope:["bot","group"],hint:"连续失败多少次后熔断停呼"},{key:"cb_cooldown_ms",label:"熔断冷却（ms）",type:"number",defaultValue:3e4,scope:["bot","group"],hint:"熔断后多久恢复"},{key:"plugin_timeout_ms",label:"插件超时（ms）",type:"number",defaultValue:5e3,scope:["bot","group"],hint:"插件执行超过此值则隔离跳过"},{key:"media_download_enabled",label:"启用媒体按需下载（本机器人）",type:"switch",defaultValue:!1,scope:["bot","group"],hint:"该机器人/群是否启用媒体下载（覆盖全局）"}],_=R(!1),h=R(!1),O=R("settings"),v=R("global"),f=R(""),m=R(""),z=R([]),P=R({}),I=R({}),V=R({}),B=se(()=>{const r=v.value==="global"?["global"]:v.value==="bot"?["bot","group"]:["group"];return y.filter(e=>!e.scope||e.scope.some(n=>r.includes(n)))}),$=se(()=>{var e;if(!f.value)return[];const r=(e=I.value)==null?void 0:e[f.value];return r?Object.entries(r).map(([n,u])=>({GROUP_ID:n,groupId:n})):[]}),L=se(()=>[{label:"（默认 = 整个机器人）",value:""},...$.value.map(r=>{const e=r.GROUP_ID||r.groupId||r.groupOpenid;return{label:e,value:e}})]);function N(r){var e,n,u,H,ie;return v.value==="global"?V.value[r.key]??"":f.value&&m.value?((u=(n=(e=I.value)==null?void 0:e[f.value])==null?void 0:n[m.value])==null?void 0:u[r.key])??"":f.value?((ie=(H=P.value)==null?void 0:H[f.value])==null?void 0:ie[r.key])??"":""}function U(r){const e=r.defaultValue;return e==null?"":String(e)}const w=R({});function X(r){return r in w.value}function G(r){const e=N(r);return e!==""&&e!==U(r)}function k(r){return X(r.key)?w.value[r.key]:N(r)}function c(r,e){w.value[r.key]=String(e)}async function W(){_.value=!0;try{const[r,e]=await Promise.all([K.getConfig(),K.getBots().catch(()=>[])]);V.value=r.global??{},P.value=r.bots??{},I.value=r.groups??{},z.value=e.map(n=>({botKey:n.botKey,appId:n.appId,platform:n.platform})),w.value={}}catch(r){s.error("加载失败："+((r==null?void 0:r.message)??r))}finally{_.value=!1}}function te(){const r=prompt("新键名（英文，例如 my_plugin.enabled）");if(!(!r||r.trim()==="")){if(y.some(e=>e.key===r)){s.warning("该键已是已知项");return}w.value[r]=""}}async function ae(){if(Object.keys(w.value).length===0){s.info("没有改动");return}h.value=!0;try{const r={...w.value};B.value.forEach(e=>{e.type==="switch"&&e.key in r&&(r[e.key]=r[e.key]==="true"||r[e.key]===!0?"true":"false")}),v.value==="global"?await K.putGlobalConfig(r):m.value?await K.putGroupConfig(f.value,m.value,r):await K.putBotConfig(f.value,r),s.success("已保存（即时生效）"),await W()}catch(r){s.error("保存失败："+((r==null?void 0:r.message)??r))}finally{h.value=!1}}async function Pe(r){try{await K.deleteConfigKey(v.value,f.value||"global",r.key,m.value||void 0),s.success(`已重置：${r.label}`),delete w.value[r.key],await W()}catch(e){s.error("重置失败："+((e==null?void 0:e.message)??e))}}async function Ie(){if(confirm("一键重置当前作用域下所有配置项到默认值？此操作不可撤销。")){for(const r of B.value)try{await K.deleteConfigKey(v.value,f.value||"global",r.key,m.value||void 0)}catch{}s.success("已一键重置当前作用域所有配置项"),await W()}}function me(){w.value={}}const ne=R(!1),ve=R({}),oe=R({});function ge(){const r=V.value.ignore_bot_messages;ne.value=r==="true"||r===!0,z.value.forEach(e=>{var u,H;const n=(H=(u=P.value)==null?void 0:u[e.appId])==null?void 0:H.ignore_bot_messages;n!==void 0&&(ve.value[e.appId]=n==="true")}),Object.entries(I.value||{}).forEach(([e,n])=>{Object.entries(n).forEach(([u,H])=>{"ignore_bot_messages"in H&&(oe.value[e]||(oe.value[e]={}),oe.value[e][u]=H.ignore_bot_messages==="true")})})}async function J(r){try{await K.putGlobalConfig({ignore_bot_messages:r?"true":"false"}),s.success(r?"已全局忽略其他机器人消息":"已取消全局忽略"),await W()}catch(e){s.error("保存失败："+(e==null?void 0:e.message))}}async function Oe(r,e){try{e?await K.putBotConfig(r,{ignore_bot_messages:"true"}):await K.deleteConfigKey("bot",r,"ignore_bot_messages"),s.success(`${r} ${e?"已忽略":"已取消忽略"}机器人消息`),await W()}catch(n){s.error("保存失败："+(n==null?void 0:n.message))}}async function We(r,e,n){try{n?await K.putGroupConfig(r,e,{ignore_bot_messages:"true"}):await K.deleteConfigKey("group",r,"ignore_bot_messages",e),s.success(`${e} ${n?"已忽略":"已取消忽略"}机器人消息`),await W()}catch(u){s.error("保存失败："+(u==null?void 0:u.message))}}function Be(){if(v.value==="global")f.value="",m.value="";else if(v.value==="bot")m.value="";else if(!m.value&&$.value.length){const r=$.value[0];m.value=r.GROUP_ID||r.groupId||r.groupOpenid||""}w.value={}}return $e(f,()=>{m.value="",w.value={}}),ut(async()=>{await W(),ge()}),(r,e)=>(T(),le("div",null,[p(ga,{title:"运行设置",subtitle:"作用域选择（全局/机器人/群）· 修改即时生效，无需重启",icon:i(ca)},{default:b(()=>[p(i(fe),{loading:_.value,onClick:W},{default:b(()=>[...e[5]||(e[5]=[S("重新加载",-1)])]),_:1},8,["loading"])]),_:1},8,["icon"]),_.value?(T(),A(i(_e),{key:0,description:"加载中…",style:{padding:"60px 0"}})):M("",!0),_.value?M("",!0):(T(),A(i(Qe),{key:1,bordered:!1,class:"settings-tabs-card"},{default:b(()=>[p(i(Ba),{value:O.value,"onUpdate:value":e[4]||(e[4]=n=>O.value=n),type:"line",animated:"",size:"large",class:"settings-tabs"},{default:b(()=>[p(i(ot),{name:"settings"},{tab:b(()=>[F("div",Ea,[p(i(ke),{size:"18",color:"#5b5bd6"},{default:b(()=>[p(i(ya))]),_:1}),e[6]||(e[6]=F("span",null,"运行设置",-1))])]),default:b(()=>[F("div",La,[p(i(Qe),{size:"small",bordered:!1,class:"scope-bar"},{default:b(()=>[p(i(tt),{align:"center",wrap:!1},{default:b(()=>[p(i(q),{strong:""},{default:b(()=>[...e[7]||(e[7]=[S("作用域：",-1)])]),_:1}),p(i(Se),{value:v.value,"onUpdate:value":[e[0]||(e[0]=n=>v.value=n),Be],options:[{label:"🌐 全局（所有机器人）",value:"global"},{label:"👥 机器人级",value:"bot"},{label:"💬 群级",value:"group"}],style:{width:"180px"}},null,8,["value"]),v.value!=="global"?(T(),A(i(q),{key:0,depth:"3"},{default:b(()=>[...e[8]||(e[8]=[S("机器人：",-1)])]),_:1})):M("",!0),v.value!=="global"?(T(),A(i(Se),{key:1,value:f.value,"onUpdate:value":e[1]||(e[1]=n=>f.value=n),options:z.value.map(n=>({label:`${n.appId}（${n.platform}）`,value:n.appId})),placeholder:"选机器人",style:{width:"220px"}},null,8,["value","options"])):M("",!0),v.value==="group"?(T(),A(i(q),{key:2,depth:"3"},{default:b(()=>[...e[9]||(e[9]=[S("群：",-1)])]),_:1})):M("",!0),v.value==="group"?(T(),A(i(Se),{key:3,value:m.value,"onUpdate:value":e[2]||(e[2]=n=>m.value=n),options:L.value,placeholder:"选群（默认 = 整个机器人）",style:{width:"260px"}},null,8,["value","options"])):M("",!0),w.value&&Object.keys(w.value).length?(T(),A(i(Re),{key:4,vertical:""})):M("",!0),Object.keys(w.value).length?(T(),A(i(pe),{key:5,type:"warning",bordered:!1},{default:b(()=>[S(re(Object.keys(w.value).length)+" 项未保存 ",1)]),_:1})):M("",!0)]),_:1})]),_:1}),p(i(Re),{style:{margin:"12px 0"}}),v.value!=="global"&&!f.value?(T(),A(i(_e),{key:0,description:"请先选机器人",style:{padding:"24px 0"}})):(T(),le("div",ja,[(T(!0),le(Te,null,Ne(B.value,n=>(T(),le("div",{key:n.key,class:fa(["config-row",{"is-dirty":X(n.key),"is-overridden":G(n)}])},[F("div",Aa,[p(i(q),{class:"config-label-text"},{default:b(()=>[S(re(n.label),1)]),_:2},1024),p(i(Je),{trigger:"hover"},{trigger:b(()=>[p(i(q),{depth:"3",class:"field-q"},{default:b(()=>[...e[10]||(e[10]=[S("?",-1)])]),_:1})]),default:b(()=>[S(" "+re(n.hint),1)]),_:2},1024),G(n)?(T(),A(i(pe),{key:0,size:"tiny",type:"success",bordered:!1},{default:b(()=>[...e[11]||(e[11]=[S("已设置",-1)])]),_:1})):M("",!0),p(i(q),{depth:"3",class:"config-key"},{default:b(()=>[S(re(n.key),1)]),_:2},1024)]),F("div",Va,[n.type==="switch"?(T(),A(i(ze),{key:0,value:k(n)==="true","onUpdate:value":u=>c(n,u)},null,8,["value","onUpdate:value"])):n.type==="number"?(T(),A(i(xa),{key:1,value:Number(k(n))||0,placeholder:String(n.defaultValue),style:{width:"100%"},min:0,"onUpdate:value":u=>c(n,u??0)},null,8,["value","placeholder","onUpdate:value"])):n.type==="select"?(T(),A(i(Se),{key:2,value:k(n)||String(n.defaultValue),options:n.options??[],style:{width:"100%"},"onUpdate:value":u=>c(n,u??String(n.defaultValue))},null,8,["value","options","onUpdate:value"])):n.type==="longtext"?(T(),A(i(at),{key:3,type:"textarea",value:k(n),placeholder:String(n.defaultValue)||"（多行文本）",autosize:{minRows:2,maxRows:5},"onUpdate:value":u=>c(n,u)},null,8,["value","placeholder","onUpdate:value"])):(T(),A(i(at),{key:4,value:k(n),placeholder:String(n.defaultValue)||"（空）","onUpdate:value":u=>c(n,u)},null,8,["value","placeholder","onUpdate:value"]))]),F("div",Na,[p(i(Je),{trigger:"hover"},{trigger:b(()=>[p(i(fe),{size:"small",quaternary:"",onClick:u=>Pe(n)},{default:b(()=>[p(i(ke),null,{default:b(()=>[p(i(Ze))]),_:1})]),_:1},8,["onClick"])]),default:b(()=>[e[12]||(e[12]=S(" 一键重置（删除该键 → 回到默认） ",-1))]),_:2},1024)])],2))),128))])),p(i(Re)),p(i(tt),null,{default:b(()=>[p(i(fe),{type:"primary",loading:h.value,disabled:Object.keys(w.value).length===0,onClick:ae},{default:b(()=>[S(" 保存配置"+re(Object.keys(w.value).length?`（${Object.keys(w.value).length} 项）`:""),1)]),_:1},8,["loading","disabled"]),Object.keys(w.value).length?(T(),A(i(fe),{key:0,quaternary:"",onClick:me},{default:b(()=>[...e[13]||(e[13]=[S("放弃改动",-1)])]),_:1})):M("",!0),p(i(Ca),{onPositiveClick:Ie},{trigger:b(()=>[p(i(fe),{quaternary:"",type:"warning"},{icon:b(()=>[p(i(ke),null,{default:b(()=>[p(i(Ze))]),_:1})]),default:b(()=>[e[14]||(e[14]=S(" 一键重置全部 ",-1))]),_:1})]),default:b(()=>[e[15]||(e[15]=S(" 当前作用域下所有配置项都会回到默认值，无法撤销。确定？ ",-1))]),_:1}),v.value==="global"?(T(),A(i(fe),{key:1,dashed:"",onClick:te},{default:b(()=>[...e[16]||(e[16]=[S("+ 新增自由键",-1)])]),_:1})):M("",!0)]),_:1}),p(i(q),{depth:"3",class:"tip"},{default:b(()=>[e[18]||(e[18]=S(" 提示：未设置项显示 ",-1)),p(i(pe),{size:"tiny",bordered:!1},{default:b(()=>[...e[17]||(e[17]=[S("灰色 placeholder",-1)])]),_:1}),e[19]||(e[19]=S("（默认值）；「已设置」绿标表示已覆盖默认；右侧 ⟳ 一键重置；底部「一键重置全部」清空当前作用域所有键。 ",-1))]),_:1})])]),_:1}),p(i(ot),{name:"ignore"},{tab:b(()=>[F("div",Ua,[p(i(ke),{size:"18",color:"#d03050"},{default:b(()=>[p(i(pa))]),_:1}),e[20]||(e[20]=F("span",null,"忽略机器人消息",-1))])]),default:b(()=>[F("div",Da,[p(i(q),{depth:"3",style:{display:"block","margin-bottom":"14px","font-size":"12px","line-height":"1.6"}},{default:b(()=>[e[23]||(e[23]=S(" 消息作者 ",-1)),p(i(pe),{size:"tiny",bordered:!1},{default:b(()=>[...e[21]||(e[21]=[S("author.bot=true",-1)])]),_:1}),e[24]||(e[24]=S(" 即其他机器人/本机器人发出的消息。",-1)),e[25]||(e[25]=F("br",null,null,-1)),e[26]||(e[26]=S(" 开启「忽略」后只记录不处理（不触发命令、不回复）。 ",-1)),p(i(pe),{size:"tiny",type:"warning",bordered:!1,style:{"margin-left":"4px"}},{default:b(()=>[...e[22]||(e[22]=[S("优先级：群级 > bot 级 > 全局",-1)])]),_:1})]),_:1}),F("div",Ha,[F("div",null,[p(i(q),{strong:""},{default:b(()=>[...e[27]||(e[27]=[S("全局",-1)])]),_:1}),p(i(q),{depth:"3",style:{"font-size":"11px","margin-left":"8px"}},{default:b(()=>[...e[28]||(e[28]=[S("忽略所有机器人的消息",-1)])]),_:1})]),p(i(ze),{value:ne.value,"onUpdate:value":e[3]||(e[3]=n=>{ne.value=n,J(n)})},null,8,["value"])]),p(i(Re),{style:{margin:"14px 0"}}),z.value.length?M("",!0):(T(),A(i(_e),{key:0,description:"暂无机器人",style:{padding:"16px 0"}})),(T(!0),le(Te,null,Ne(z.value,n=>(T(),le("div",{key:n.appId,class:"ignore-bot-block"},[F("div",Fa,[F("div",null,[p(i(q),{strong:""},{default:b(()=>[S(re(n.appId),1)]),_:2},1024),p(i(pe),{size:"tiny",bordered:!1,type:"info",style:{"margin-left":"6px"}},{default:b(()=>[S(re($.value.filter(u=>{var H;return(u.GROUP_ID||u.groupId)&&(((H=I.value[n.appId])==null?void 0:H[u.GROUP_ID||u.groupId])||Object.keys(I.value[n.appId]||{}).length>0)}).length||Object.keys(I.value[n.appId]||{}).length)+" 个群 ",1)]),_:2},1024)]),p(i(ze),{value:!!ve.value[n.appId],"onUpdate:value":u=>Oe(n.appId,u)},null,8,["value","onUpdate:value"])]),F("div",Ga,[$.value.length?M("",!0):(T(),A(i(_e),{key:0,size:"small",description:"该机器人暂无群数据（正常接收消息后会建群）",style:{padding:"8px 0"}})),(T(!0),le(Te,null,Ne($.value,u=>{var H;return T(),le("div",{key:u.GROUP_ID||u.groupId||u.groupOpenid,class:"ignore-group-row"},[F("div",Ma,[p(i(q),{class:"ignore-group-name"},{default:b(()=>[S(re(i(ha)(u)),1)]),_:2},1024),p(i(q),{depth:"3",style:{"font-size":"11px","font-family":"ui-monospace, SFMono-Regular, monospace"}},{default:b(()=>[S(re(u.GROUP_ID||u.groupId||u.groupOpenid),1)]),_:2},1024)]),p(i(ze),{size:"small",value:!!((H=oe.value[n.appId])!=null&&H[u.GROUP_ID||u.groupId||u.groupOpenid]),"onUpdate:value":ie=>We(n.appId,u.GROUP_ID||u.groupId||u.groupOpenid,ie)},null,8,["value","onUpdate:value"])])}),128))])]))),128))])]),_:1})]),_:1},8,["value"])]),_:1}))]))}}),vo=va(qa,[["__scopeId","data-v-fb7460e2"]]);export{vo as default};
