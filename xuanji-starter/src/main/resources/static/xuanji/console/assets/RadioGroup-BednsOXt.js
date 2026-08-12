import{o as ee,u as $,bw as A,_ as k,b5 as G,bI as U,y as B,l as oe,w as T,c as _,d as g,a as C,b as V,e as E,f as te,g as re,h as j,k as N,j as ne,q as ae,v as P,cl as ie,x as D,p as de}from"./index-B04mKnMx.js";import{g as se}from"./get-slot-Bk_rJcZu.js";const fe={name:String,value:{type:[String,Number,Boolean],default:"on"},checked:{type:Boolean,default:void 0},defaultChecked:Boolean,disabled:{type:Boolean,default:void 0},label:String,size:String,onUpdateChecked:[Function,Array],"onUpdate:checked":[Function,Array],checkedValue:{type:Boolean,default:void 0}},H=oe("n-radio-group");function he(e){const o=ee(H,null),{mergedClsPrefixRef:n,mergedComponentPropsRef:d}=$(e),i=A(e,{mergedSize(t){var r,a;const{size:v}=e;if(v!==void 0)return v;if(o){const{mergedSizeRef:{value:F}}=o;if(F!==void 0)return F}if(t)return t.mergedSize.value;const I=(a=(r=d==null?void 0:d.value)===null||r===void 0?void 0:r.Radio)===null||a===void 0?void 0:a.size;return I||"medium"},mergedDisabled(t){return!!(e.disabled||o!=null&&o.disabledRef.value||t!=null&&t.disabled.value)}}),{mergedSizeRef:f,mergedDisabledRef:s}=i,l=k(null),u=k(null),h=k(e.defaultChecked),p=T(e,"checked"),m=G(p,h),c=U(()=>o?o.valueRef.value===e.value:m.value),R=U(()=>{const{name:t}=e;if(t!==void 0)return t;if(o)return o.nameRef.value}),b=k(!1);function y(){if(o){const{doUpdateValue:t}=o,{value:r}=e;B(t,r)}else{const{onUpdateChecked:t,"onUpdate:checked":r}=e,{nTriggerFormInput:a,nTriggerFormChange:v}=i;t&&B(t,!0),r&&B(r,!0),a(),v(),h.value=!0}}function z(){s.value||c.value||y()}function S(){z(),l.value&&(l.value.checked=c.value)}function w(){b.value=!1}function x(){b.value=!0}return{mergedClsPrefix:o?o.mergedClsPrefixRef:n,inputRef:l,labelRef:u,mergedName:R,mergedDisabled:s,renderSafeChecked:c,focus:b,mergedSize:f,handleRadioInputChange:S,handleRadioInputBlur:w,handleRadioInputFocus:x}}const le=_("radio-group",`
 display: inline-block;
 font-size: var(--n-font-size);
`,[g("splitor",`
 display: inline-block;
 vertical-align: bottom;
 width: 1px;
 transition:
 background-color .3s var(--n-bezier),
 opacity .3s var(--n-bezier);
 background: var(--n-button-border-color);
 `,[C("checked",{backgroundColor:"var(--n-button-border-color-active)"}),C("disabled",{opacity:"var(--n-opacity-disabled)"})]),C("button-group",`
 white-space: nowrap;
 height: var(--n-height);
 line-height: var(--n-height);
 `,[_("radio-button",{height:"var(--n-height)",lineHeight:"var(--n-height)"}),g("splitor",{height:"var(--n-height)"})]),_("radio-button",`
 vertical-align: bottom;
 outline: none;
 position: relative;
 user-select: none;
 -webkit-user-select: none;
 display: inline-block;
 box-sizing: border-box;
 padding-left: 14px;
 padding-right: 14px;
 white-space: nowrap;
 transition:
 background-color .3s var(--n-bezier),
 opacity .3s var(--n-bezier),
 border-color .3s var(--n-bezier),
 color .3s var(--n-bezier);
 background: var(--n-button-color);
 color: var(--n-button-text-color);
 border-top: 1px solid var(--n-button-border-color);
 border-bottom: 1px solid var(--n-button-border-color);
 `,[_("radio-input",`
 pointer-events: none;
 position: absolute;
 border: 0;
 border-radius: inherit;
 left: 0;
 right: 0;
 top: 0;
 bottom: 0;
 opacity: 0;
 z-index: 1;
 `),g("state-border",`
 z-index: 1;
 pointer-events: none;
 position: absolute;
 box-shadow: var(--n-button-box-shadow);
 transition: box-shadow .3s var(--n-bezier);
 left: -1px;
 bottom: -1px;
 right: -1px;
 top: -1px;
 `),V("&:first-child",`
 border-top-left-radius: var(--n-button-border-radius);
 border-bottom-left-radius: var(--n-button-border-radius);
 border-left: 1px solid var(--n-button-border-color);
 `,[g("state-border",`
 border-top-left-radius: var(--n-button-border-radius);
 border-bottom-left-radius: var(--n-button-border-radius);
 `)]),V("&:last-child",`
 border-top-right-radius: var(--n-button-border-radius);
 border-bottom-right-radius: var(--n-button-border-radius);
 border-right: 1px solid var(--n-button-border-color);
 `,[g("state-border",`
 border-top-right-radius: var(--n-button-border-radius);
 border-bottom-right-radius: var(--n-button-border-radius);
 `)]),E("disabled",`
 cursor: pointer;
 `,[V("&:hover",[g("state-border",`
 transition: box-shadow .3s var(--n-bezier);
 box-shadow: var(--n-button-box-shadow-hover);
 `),E("checked",{color:"var(--n-button-text-color-hover)"})]),C("focus",[V("&:not(:active)",[g("state-border",{boxShadow:"var(--n-button-box-shadow-focus)"})])])]),C("checked",`
 background: var(--n-button-color-active);
 color: var(--n-button-text-color-active);
 border-color: var(--n-button-border-color-active);
 `),C("disabled",`
 cursor: not-allowed;
 opacity: var(--n-opacity-disabled);
 `)])]);function ue(e,o,n){var d;const i=[];let f=!1;for(let s=0;s<e.length;++s){const l=e[s],u=(d=l.type)===null||d===void 0?void 0:d.name;u==="RadioButton"&&(f=!0);const h=l.props;if(u!=="RadioButton"){i.push(l);continue}if(s===0)i.push(l);else{const p=i[i.length-1].props,m=o===p.value,c=p.disabled,R=o===h.value,b=h.disabled,y=(m?2:0)+(c?0:1),z=(R?2:0)+(b?0:1),S={[`${n}-radio-group__splitor--disabled`]:c,[`${n}-radio-group__splitor--checked`]:m},w={[`${n}-radio-group__splitor--disabled`]:b,[`${n}-radio-group__splitor--checked`]:R},x=y<z?w:S;i.push(j("div",{class:[`${n}-radio-group__splitor`,x]}),l)}}return{children:i,isButtonGroup:f}}const ce=Object.assign(Object.assign({},N.props),{name:String,value:[String,Number,Boolean],defaultValue:{type:[String,Number,Boolean],default:null},size:String,disabled:{type:Boolean,default:void 0},"onUpdate:value":[Function,Array],onUpdateValue:[Function,Array]}),ge=te({name:"RadioGroup",props:ce,setup(e){const o=k(null),{mergedSizeRef:n,mergedDisabledRef:d,nTriggerFormChange:i,nTriggerFormInput:f,nTriggerFormBlur:s,nTriggerFormFocus:l}=A(e),{mergedClsPrefixRef:u,inlineThemeDisabled:h,mergedRtlRef:p}=$(e),m=N("Radio","-radio-group",le,ie,e,u),c=k(e.defaultValue),R=T(e,"value"),b=G(R,c);function y(r){const{onUpdateValue:a,"onUpdate:value":v}=e;a&&B(a,r),v&&B(v,r),c.value=r,i(),f()}function z(r){const{value:a}=o;a&&(a.contains(r.relatedTarget)||l())}function S(r){const{value:a}=o;a&&(a.contains(r.relatedTarget)||s())}de(H,{mergedClsPrefixRef:u,nameRef:T(e,"name"),valueRef:b,disabledRef:d,mergedSizeRef:n,doUpdateValue:y});const w=ne("Radio",p,u),x=P(()=>{const{value:r}=n,{common:{cubicBezierEaseInOut:a},self:{buttonBorderColor:v,buttonBorderColorActive:I,buttonBorderRadius:F,buttonBoxShadow:M,buttonBoxShadowFocus:K,buttonBoxShadowHover:O,buttonColor:q,buttonColorActive:L,buttonTextColor:J,buttonTextColorActive:Q,buttonTextColorHover:W,opacityDisabled:X,[D("buttonHeight",r)]:Y,[D("fontSize",r)]:Z}}=m.value;return{"--n-font-size":Z,"--n-bezier":a,"--n-button-border-color":v,"--n-button-border-color-active":I,"--n-button-border-radius":F,"--n-button-box-shadow":M,"--n-button-box-shadow-focus":K,"--n-button-box-shadow-hover":O,"--n-button-color":q,"--n-button-color-active":L,"--n-button-text-color":J,"--n-button-text-color-hover":W,"--n-button-text-color-active":Q,"--n-height":Y,"--n-opacity-disabled":X}}),t=h?ae("radio-group",P(()=>n.value[0]),x,e):void 0;return{selfElRef:o,rtlEnabled:w,mergedClsPrefix:u,mergedValue:b,handleFocusout:S,handleFocusin:z,cssVars:h?void 0:x,themeClass:t==null?void 0:t.themeClass,onRender:t==null?void 0:t.onRender}},render(){var e;const{mergedValue:o,mergedClsPrefix:n,handleFocusin:d,handleFocusout:i}=this,{children:f,isButtonGroup:s}=ue(re(se(this)),o,n);return(e=this.onRender)===null||e===void 0||e.call(this),j("div",{onFocusin:d,onFocusout:i,ref:"selfElRef",class:[`${n}-radio-group`,this.rtlEnabled&&`${n}-radio-group--rtl`,this.themeClass,s&&`${n}-radio-group--button-group`],style:this.cssVars},f)}});export{ge as N,fe as r,he as s};
