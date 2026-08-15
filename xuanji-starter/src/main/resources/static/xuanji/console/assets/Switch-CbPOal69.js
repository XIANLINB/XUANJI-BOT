import{c as M,d as i,i as E,b as D,a as r,e as I,f as ce,c9 as K,h as a,r as v,N as de,ca as ue,u as he,k as L,bA as be,Z as O,b9 as fe,q as ve,v as F,cb as ge,y as U,x as g,bJ as A,cc as l,w as we}from"./index-B7VD0f6B.js";const me=M("switch",`
 height: var(--n-height);
 min-width: var(--n-width);
 vertical-align: middle;
 user-select: none;
 -webkit-user-select: none;
 display: inline-flex;
 outline: none;
 justify-content: center;
 align-items: center;
`,[i("children-placeholder",`
 height: var(--n-rail-height);
 display: flex;
 flex-direction: column;
 overflow: hidden;
 pointer-events: none;
 visibility: hidden;
 `),i("rail-placeholder",`
 display: flex;
 flex-wrap: none;
 `),i("button-placeholder",`
 width: calc(1.75 * var(--n-rail-height));
 height: var(--n-rail-height);
 `),M("base-loading",`
 position: absolute;
 top: 50%;
 left: 50%;
 transform: translateX(-50%) translateY(-50%);
 font-size: calc(var(--n-button-width) - 4px);
 color: var(--n-loading-color);
 transition: color .3s var(--n-bezier);
 `,[E({left:"50%",top:"50%",originalTransform:"translateX(-50%) translateY(-50%)"})]),i("checked, unchecked",`
 transition: color .3s var(--n-bezier);
 color: var(--n-text-color);
 box-sizing: border-box;
 position: absolute;
 white-space: nowrap;
 top: 0;
 bottom: 0;
 display: flex;
 align-items: center;
 line-height: 1;
 `),i("checked",`
 right: 0;
 padding-right: calc(1.25 * var(--n-rail-height) - var(--n-offset));
 `),i("unchecked",`
 left: 0;
 justify-content: flex-end;
 padding-left: calc(1.25 * var(--n-rail-height) - var(--n-offset));
 `),D("&:focus",[i("rail",`
 box-shadow: var(--n-box-shadow-focus);
 `)]),r("round",[i("rail","border-radius: calc(var(--n-rail-height) / 2);",[i("button","border-radius: calc(var(--n-button-height) / 2);")])]),I("disabled",[I("icon",[r("rubber-band",[r("pressed",[i("rail",[i("button","max-width: var(--n-button-width-pressed);")])]),i("rail",[D("&:active",[i("button","max-width: var(--n-button-width-pressed);")])]),r("active",[r("pressed",[i("rail",[i("button","left: calc(100% - var(--n-offset) - var(--n-button-width-pressed));")])]),i("rail",[D("&:active",[i("button","left: calc(100% - var(--n-offset) - var(--n-button-width-pressed));")])])])])])]),r("active",[i("rail",[i("button","left: calc(100% - var(--n-button-width) - var(--n-offset))")])]),i("rail",`
 overflow: hidden;
 height: var(--n-rail-height);
 min-width: var(--n-rail-width);
 border-radius: var(--n-rail-border-radius);
 cursor: pointer;
 position: relative;
 transition:
 opacity .3s var(--n-bezier),
 background .3s var(--n-bezier),
 box-shadow .3s var(--n-bezier);
 background-color: var(--n-rail-color);
 `,[i("button-icon",`
 color: var(--n-icon-color);
 transition: color .3s var(--n-bezier);
 font-size: calc(var(--n-button-height) - 4px);
 position: absolute;
 left: 0;
 right: 0;
 top: 0;
 bottom: 0;
 display: flex;
 justify-content: center;
 align-items: center;
 line-height: 1;
 `,[E()]),i("button",`
 align-items: center; 
 top: var(--n-offset);
 left: var(--n-offset);
 height: var(--n-button-height);
 width: var(--n-button-width-pressed);
 max-width: var(--n-button-width);
 border-radius: var(--n-button-border-radius);
 background-color: var(--n-button-color);
 box-shadow: var(--n-button-box-shadow);
 box-sizing: border-box;
 cursor: inherit;
 content: "";
 position: absolute;
 transition:
 background-color .3s var(--n-bezier),
 left .3s var(--n-bezier),
 opacity .3s var(--n-bezier),
 max-width .3s var(--n-bezier),
 box-shadow .3s var(--n-bezier);
 `)]),r("active",[i("rail","background-color: var(--n-rail-color-active);")]),r("loading",[i("rail",`
 cursor: wait;
 `)]),r("disabled",[i("rail",`
 cursor: not-allowed;
 opacity: .5;
 `)])]),pe=Object.assign(Object.assign({},L.props),{size:String,value:{type:[String,Number,Boolean],default:void 0},loading:Boolean,defaultValue:{type:[String,Number,Boolean],default:!1},disabled:{type:Boolean,default:void 0},round:{type:Boolean,default:!0},"onUpdate:value":[Function,Array],onUpdateValue:[Function,Array],checkedValue:{type:[String,Number,Boolean],default:!0},uncheckedValue:{type:[String,Number,Boolean],default:!1},railStyle:Function,rubberBand:{type:Boolean,default:!0},spinProps:Object,onChange:[Function,Array]});let C;const ke=ce({name:"Switch",props:pe,slots:Object,setup(e){C===void 0&&(typeof CSS<"u"?typeof CSS.supports<"u"?C=CSS.supports("width","max(1px)"):C=!1:C=!0);const{mergedClsPrefixRef:_,inlineThemeDisabled:p,mergedComponentPropsRef:y}=he(e),k=L("Switch","-switch",me,ge,e,_),w=be(e,{mergedSize(t){var s,c;if(e.size!==void 0)return e.size;if(t)return t.mergedSize.value;const f=(c=(s=y==null?void 0:y.value)===null||s===void 0?void 0:s.Switch)===null||c===void 0?void 0:c.size;return f||"medium"}}),{mergedSizeRef:x,mergedDisabledRef:h}=w,S=O(e.defaultValue),R=we(e,"value"),b=fe(R,S),B=F(()=>b.value===e.checkedValue),n=O(!1),o=O(!1),$=F(()=>{const{railStyle:t}=e;if(t)return t({focused:o.value,checked:B.value})});function V(t){const{"onUpdate:value":s,onChange:c,onUpdateValue:f}=e,{nTriggerFormInput:P,nTriggerFormChange:T}=w;s&&U(s,t),f&&U(f,t),c&&U(c,t),S.value=t,P(),T()}function X(){const{nTriggerFormFocus:t}=w;t()}function Y(){const{nTriggerFormBlur:t}=w;t()}function q(){e.loading||h.value||(b.value!==e.checkedValue?V(e.checkedValue):V(e.uncheckedValue))}function J(){o.value=!0,X()}function Z(){o.value=!1,Y(),n.value=!1}function G(t){e.loading||h.value||t.key===" "&&(b.value!==e.checkedValue?V(e.checkedValue):V(e.uncheckedValue),n.value=!1)}function Q(t){e.loading||h.value||t.key===" "&&(t.preventDefault(),n.value=!0)}const H=F(()=>{const{value:t}=x,{self:{opacityDisabled:s,railColor:c,railColorActive:f,buttonBoxShadow:P,buttonColor:T,boxShadowFocus:ee,loadingColor:te,textColor:ie,iconColor:ne,[g("buttonHeight",t)]:d,[g("buttonWidth",t)]:ae,[g("buttonWidthPressed",t)]:oe,[g("railHeight",t)]:u,[g("railWidth",t)]:z,[g("railBorderRadius",t)]:re,[g("buttonBorderRadius",t)]:le},common:{cubicBezierEaseInOut:se}}=k.value;let N,j,W;return C?(N=`calc((${u} - ${d}) / 2)`,j=`max(${u}, ${d})`,W=`max(${z}, calc(${z} + ${d} - ${u}))`):(N=A((l(u)-l(d))/2),j=A(Math.max(l(u),l(d))),W=l(u)>l(d)?z:A(l(z)+l(d)-l(u))),{"--n-bezier":se,"--n-button-border-radius":le,"--n-button-box-shadow":P,"--n-button-color":T,"--n-button-width":ae,"--n-button-width-pressed":oe,"--n-button-height":d,"--n-height":j,"--n-offset":N,"--n-opacity-disabled":s,"--n-rail-border-radius":re,"--n-rail-color":c,"--n-rail-color-active":f,"--n-rail-height":u,"--n-rail-width":z,"--n-width":W,"--n-box-shadow-focus":ee,"--n-loading-color":te,"--n-text-color":ie,"--n-icon-color":ne}}),m=p?ve("switch",F(()=>x.value[0]),H,e):void 0;return{handleClick:q,handleBlur:Z,handleFocus:J,handleKeyup:G,handleKeydown:Q,mergedRailStyle:$,pressed:n,mergedClsPrefix:_,mergedValue:b,checked:B,mergedDisabled:h,cssVars:p?void 0:H,themeClass:m==null?void 0:m.themeClass,onRender:m==null?void 0:m.onRender}},render(){const{mergedClsPrefix:e,mergedDisabled:_,checked:p,mergedRailStyle:y,onRender:k,$slots:w}=this;k==null||k();const{checked:x,unchecked:h,icon:S,"checked-icon":R,"unchecked-icon":b}=w,B=!(K(S)&&K(R)&&K(b));return a("div",{role:"switch","aria-checked":p,class:[`${e}-switch`,this.themeClass,B&&`${e}-switch--icon`,p&&`${e}-switch--active`,_&&`${e}-switch--disabled`,this.round&&`${e}-switch--round`,this.loading&&`${e}-switch--loading`,this.pressed&&`${e}-switch--pressed`,this.rubberBand&&`${e}-switch--rubber-band`],tabindex:this.mergedDisabled?void 0:0,style:this.cssVars,onClick:this.handleClick,onFocus:this.handleFocus,onBlur:this.handleBlur,onKeyup:this.handleKeyup,onKeydown:this.handleKeydown},a("div",{class:`${e}-switch__rail`,"aria-hidden":"true",style:y},v(x,n=>v(h,o=>n||o?a("div",{"aria-hidden":!0,class:`${e}-switch__children-placeholder`},a("div",{class:`${e}-switch__rail-placeholder`},a("div",{class:`${e}-switch__button-placeholder`}),n),a("div",{class:`${e}-switch__rail-placeholder`},a("div",{class:`${e}-switch__button-placeholder`}),o)):null)),a("div",{class:`${e}-switch__button`},v(S,n=>v(R,o=>v(b,$=>a(de,null,{default:()=>this.loading?a(ue,Object.assign({key:"loading",clsPrefix:e,strokeWidth:20},this.spinProps)):this.checked&&(o||n)?a("div",{class:`${e}-switch__button-icon`,key:o?"checked-icon":"icon"},o||n):!this.checked&&($||n)?a("div",{class:`${e}-switch__button-icon`,key:$?"unchecked-icon":"icon"},$||n):null})))),v(x,n=>n&&a("div",{key:"checked",class:`${e}-switch__checked`},n)),v(h,n=>n&&a("div",{key:"unchecked",class:`${e}-switch__unchecked`},n)))))}});export{ke as N};
