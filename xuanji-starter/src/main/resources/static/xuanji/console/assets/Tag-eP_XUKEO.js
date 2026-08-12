import{f as I,h as s,c as M,d as v,b as y,m as de,u as L,k as $,q as T,v as f,cu as he,x as a,a as C,e as _,r as H,ba as ve,j as ge,_ as ue,y as be,cv as Ce,bY as fe,cw as S,p as me,w as pe,l as ke}from"./index-LY-adOBy.js";import{u as xe}from"./use-locale-snm0p2zq.js";const ze=I({name:"Empty",render(){return s("svg",{viewBox:"0 0 28 28",fill:"none",xmlns:"http://www.w3.org/2000/svg"},s("path",{d:"M26 7.5C26 11.0899 23.0899 14 19.5 14C15.9101 14 13 11.0899 13 7.5C13 3.91015 15.9101 1 19.5 1C23.0899 1 26 3.91015 26 7.5ZM16.8536 4.14645C16.6583 3.95118 16.3417 3.95118 16.1464 4.14645C15.9512 4.34171 15.9512 4.65829 16.1464 4.85355L18.7929 7.5L16.1464 10.1464C15.9512 10.3417 15.9512 10.6583 16.1464 10.8536C16.3417 11.0488 16.6583 11.0488 16.8536 10.8536L19.5 8.20711L22.1464 10.8536C22.3417 11.0488 22.6583 11.0488 22.8536 10.8536C23.0488 10.6583 23.0488 10.3417 22.8536 10.1464L20.2071 7.5L22.8536 4.85355C23.0488 4.65829 23.0488 4.34171 22.8536 4.14645C22.6583 3.95118 22.3417 3.95118 22.1464 4.14645L19.5 6.79289L16.8536 4.14645Z",fill:"currentColor"}),s("path",{d:"M25 22.75V12.5991C24.5572 13.0765 24.053 13.4961 23.5 13.8454V16H17.5L17.3982 16.0068C17.0322 16.0565 16.75 16.3703 16.75 16.75C16.75 18.2688 15.5188 19.5 14 19.5C12.4812 19.5 11.25 18.2688 11.25 16.75L11.2432 16.6482C11.1935 16.2822 10.8797 16 10.5 16H4.5V7.25C4.5 6.2835 5.2835 5.5 6.25 5.5H12.2696C12.4146 4.97463 12.6153 4.47237 12.865 4H6.25C4.45507 4 3 5.45507 3 7.25V22.75C3 24.5449 4.45507 26 6.25 26H21.75C23.5449 26 25 24.5449 25 22.75ZM4.5 22.75V17.5H9.81597L9.85751 17.7041C10.2905 19.5919 11.9808 21 14 21L14.215 20.9947C16.2095 20.8953 17.842 19.4209 18.184 17.5H23.5V22.75C23.5 23.7165 22.7165 24.5 21.75 24.5H6.25C5.2835 24.5 4.5 23.7165 4.5 22.75Z",fill:"currentColor"}))}}),ye=M("empty",`
 display: flex;
 flex-direction: column;
 align-items: center;
 font-size: var(--n-font-size);
`,[v("icon",`
 width: var(--n-icon-size);
 height: var(--n-icon-size);
 font-size: var(--n-icon-size);
 line-height: var(--n-icon-size);
 color: var(--n-icon-color);
 transition:
 color .3s var(--n-bezier);
 `,[y("+",[v("description",`
 margin-top: 8px;
 `)])]),v("description",`
 transition: color .3s var(--n-bezier);
 color: var(--n-text-color);
 `),v("extra",`
 text-align: center;
 transition: color .3s var(--n-bezier);
 margin-top: 12px;
 color: var(--n-extra-text-color);
 `)]),Re=Object.assign(Object.assign({},$.props),{description:String,showDescription:{type:Boolean,default:!0},showIcon:{type:Boolean,default:!0},size:{type:String,default:"medium"},renderIcon:Function}),He=I({name:"Empty",props:Re,slots:Object,setup(o){const{mergedClsPrefixRef:l,inlineThemeDisabled:n,mergedComponentPropsRef:i}=L(o),m=$("Empty","-empty",ye,he,o,l),{localeRef:z}=xe("Empty"),g=f(()=>{var t,r,k;return(t=o.description)!==null&&t!==void 0?t:(k=(r=i==null?void 0:i.value)===null||r===void 0?void 0:r.Empty)===null||k===void 0?void 0:k.description}),u=f(()=>{var t,r;return((r=(t=i==null?void 0:i.value)===null||t===void 0?void 0:t.Empty)===null||r===void 0?void 0:r.renderIcon)||(()=>s(ze,null))}),p=f(()=>{const{size:t}=o,{common:{cubicBezierEaseInOut:r},self:{[a("iconSize",t)]:k,[a("fontSize",t)]:R,textColor:b,iconColor:e,extraTextColor:c}}=m.value;return{"--n-icon-size":k,"--n-font-size":R,"--n-bezier":r,"--n-text-color":b,"--n-icon-color":e,"--n-extra-text-color":c}}),d=n?T("empty",f(()=>{let t="";const{size:r}=o;return t+=r[0],t}),p,o):void 0;return{mergedClsPrefix:l,mergedRenderIcon:u,localizedDescription:f(()=>g.value||z.value.description),cssVars:n?void 0:p,themeClass:d==null?void 0:d.themeClass,onRender:d==null?void 0:d.onRender}},render(){const{$slots:o,mergedClsPrefix:l,onRender:n}=this;return n==null||n(),s("div",{class:[`${l}-empty`,this.themeClass],style:this.cssVars},this.showIcon?s("div",{class:`${l}-empty__icon`},o.icon?o.icon():s(de,{clsPrefix:l},{default:this.mergedRenderIcon})):null,this.showDescription?s("div",{class:`${l}-empty__description`},o.default?o.default():this.localizedDescription):null,o.extra?s("div",{class:`${l}-empty__extra`},o.extra()):null)}}),Be={color:Object,type:{type:String,default:"default"},round:Boolean,size:String,closable:Boolean,disabled:{type:Boolean,default:void 0}},_e=M("tag",`
 --n-close-margin: var(--n-close-margin-top) var(--n-close-margin-right) var(--n-close-margin-bottom) var(--n-close-margin-left);
 white-space: nowrap;
 position: relative;
 box-sizing: border-box;
 cursor: default;
 display: inline-flex;
 align-items: center;
 flex-wrap: nowrap;
 padding: var(--n-padding);
 border-radius: var(--n-border-radius);
 color: var(--n-text-color);
 background-color: var(--n-color);
 transition: 
 border-color .3s var(--n-bezier),
 background-color .3s var(--n-bezier),
 color .3s var(--n-bezier),
 box-shadow .3s var(--n-bezier),
 opacity .3s var(--n-bezier);
 line-height: 1;
 height: var(--n-height);
 font-size: var(--n-font-size);
`,[C("strong",`
 font-weight: var(--n-font-weight-strong);
 `),v("border",`
 pointer-events: none;
 position: absolute;
 left: 0;
 right: 0;
 top: 0;
 bottom: 0;
 border-radius: inherit;
 border: var(--n-border);
 transition: border-color .3s var(--n-bezier);
 `),v("icon",`
 display: flex;
 margin: 0 4px 0 0;
 color: var(--n-text-color);
 transition: color .3s var(--n-bezier);
 font-size: var(--n-avatar-size-override);
 `),v("avatar",`
 display: flex;
 margin: 0 6px 0 0;
 `),v("close",`
 margin: var(--n-close-margin);
 transition:
 background-color .3s var(--n-bezier),
 color .3s var(--n-bezier);
 `),C("round",`
 padding: 0 calc(var(--n-height) / 3);
 border-radius: calc(var(--n-height) / 2);
 `,[v("icon",`
 margin: 0 4px 0 calc((var(--n-height) - 8px) / -2);
 `),v("avatar",`
 margin: 0 6px 0 calc((var(--n-height) - 8px) / -2);
 `),C("closable",`
 padding: 0 calc(var(--n-height) / 4) 0 calc(var(--n-height) / 3);
 `)]),C("icon, avatar",[C("round",`
 padding: 0 calc(var(--n-height) / 3) 0 calc(var(--n-height) / 2);
 `)]),C("disabled",`
 cursor: not-allowed !important;
 opacity: var(--n-opacity-disabled);
 `),C("checkable",`
 cursor: pointer;
 box-shadow: none;
 color: var(--n-text-color-checkable);
 background-color: var(--n-color-checkable);
 `,[_("disabled",[y("&:hover","background-color: var(--n-color-hover-checkable);",[_("checked","color: var(--n-text-color-hover-checkable);")]),y("&:active","background-color: var(--n-color-pressed-checkable);",[_("checked","color: var(--n-text-color-pressed-checkable);")])]),C("checked",`
 color: var(--n-text-color-checked);
 background-color: var(--n-color-checked);
 `,[_("disabled",[y("&:hover","background-color: var(--n-color-checked-hover);"),y("&:active","background-color: var(--n-color-checked-pressed);")])])])]),$e=Object.assign(Object.assign(Object.assign({},$.props),Be),{bordered:{type:Boolean,default:void 0},checked:Boolean,checkable:Boolean,strong:Boolean,triggerClickOnClose:Boolean,onClose:[Array,Function],onMouseenter:Function,onMouseleave:Function,"onUpdate:checked":Function,onUpdateChecked:Function,internalCloseFocusable:{type:Boolean,default:!0},internalCloseIsButtonTag:{type:Boolean,default:!0},onCheckedChange:Function}),Ie=ke("n-tag"),Se=I({name:"Tag",props:$e,slots:Object,setup(o){const l=ue(null),{mergedBorderedRef:n,mergedClsPrefixRef:i,inlineThemeDisabled:m,mergedRtlRef:z,mergedComponentPropsRef:g}=L(o),u=f(()=>{var e,c;return o.size||((c=(e=g==null?void 0:g.value)===null||e===void 0?void 0:e.Tag)===null||c===void 0?void 0:c.size)||"medium"}),p=$("Tag","-tag",_e,Ce,o,i);me(Ie,{roundRef:pe(o,"round")});function d(){if(!o.disabled&&o.checkable){const{checked:e,onCheckedChange:c,onUpdateChecked:x,"onUpdate:checked":h}=o;x&&x(!e),h&&h(!e),c&&c(!e)}}function t(e){if(o.triggerClickOnClose||e.stopPropagation(),!o.disabled){const{onClose:c}=o;c&&be(c,e)}}const r={setTextContent(e){const{value:c}=l;c&&(c.textContent=e)}},k=ge("Tag",z,i),R=f(()=>{const{type:e,color:{color:c,textColor:x}={}}=o,h=u.value,{common:{cubicBezierEaseInOut:E},self:{padding:O,closeMargin:j,borderRadius:V,opacityDisabled:D,textColorCheckable:F,textColorHoverCheckable:N,textColorPressedCheckable:U,textColorChecked:Z,colorCheckable:K,colorHoverCheckable:W,colorPressedCheckable:q,colorChecked:A,colorCheckedHover:Y,colorCheckedPressed:G,closeBorderRadius:J,fontWeightStrong:Q,[a("colorBordered",e)]:X,[a("closeSize",h)]:ee,[a("closeIconSize",h)]:oe,[a("fontSize",h)]:ne,[a("height",h)]:w,[a("color",e)]:re,[a("textColor",e)]:te,[a("border",e)]:ce,[a("closeIconColor",e)]:P,[a("closeIconColorHover",e)]:le,[a("closeIconColorPressed",e)]:ae,[a("closeColorHover",e)]:se,[a("closeColorPressed",e)]:ie}}=p.value,B=fe(j);return{"--n-font-weight-strong":Q,"--n-avatar-size-override":`calc(${w} - 8px)`,"--n-bezier":E,"--n-border-radius":V,"--n-border":ce,"--n-close-icon-size":oe,"--n-close-color-pressed":ie,"--n-close-color-hover":se,"--n-close-border-radius":J,"--n-close-icon-color":P,"--n-close-icon-color-hover":le,"--n-close-icon-color-pressed":ae,"--n-close-icon-color-disabled":P,"--n-close-margin-top":B.top,"--n-close-margin-right":B.right,"--n-close-margin-bottom":B.bottom,"--n-close-margin-left":B.left,"--n-close-size":ee,"--n-color":c||(n.value?X:re),"--n-color-checkable":K,"--n-color-checked":A,"--n-color-checked-hover":Y,"--n-color-checked-pressed":G,"--n-color-hover-checkable":W,"--n-color-pressed-checkable":q,"--n-font-size":ne,"--n-height":w,"--n-opacity-disabled":D,"--n-padding":O,"--n-text-color":x||te,"--n-text-color-checkable":F,"--n-text-color-checked":Z,"--n-text-color-hover-checkable":N,"--n-text-color-pressed-checkable":U}}),b=m?T("tag",f(()=>{let e="";const{type:c,color:{color:x,textColor:h}={}}=o;return e+=c[0],e+=u.value[0],x&&(e+=`a${S(x)}`),h&&(e+=`b${S(h)}`),n.value&&(e+="c"),e}),R,o):void 0;return Object.assign(Object.assign({},r),{rtlEnabled:k,mergedClsPrefix:i,contentRef:l,mergedBordered:n,handleClick:d,handleCloseClick:t,cssVars:m?void 0:R,themeClass:b==null?void 0:b.themeClass,onRender:b==null?void 0:b.onRender})},render(){var o,l;const{mergedClsPrefix:n,rtlEnabled:i,closable:m,color:{borderColor:z}={},round:g,onRender:u,$slots:p}=this;u==null||u();const d=H(p.avatar,r=>r&&s("div",{class:`${n}-tag__avatar`},r)),t=H(p.icon,r=>r&&s("div",{class:`${n}-tag__icon`},r));return s("div",{class:[`${n}-tag`,this.themeClass,{[`${n}-tag--rtl`]:i,[`${n}-tag--strong`]:this.strong,[`${n}-tag--disabled`]:this.disabled,[`${n}-tag--checkable`]:this.checkable,[`${n}-tag--checked`]:this.checkable&&this.checked,[`${n}-tag--round`]:g,[`${n}-tag--avatar`]:d,[`${n}-tag--icon`]:t,[`${n}-tag--closable`]:m}],style:this.cssVars,onClick:this.handleClick,onMouseenter:this.onMouseenter,onMouseleave:this.onMouseleave},t||d,s("span",{class:`${n}-tag__content`,ref:"contentRef"},(l=(o=this.$slots).default)===null||l===void 0?void 0:l.call(o)),!this.checkable&&m?s(ve,{clsPrefix:n,class:`${n}-tag__close`,disabled:this.disabled,onClick:this.handleCloseClick,focusable:this.internalCloseFocusable,round:g,isButtonTag:this.internalCloseIsButtonTag,absolute:!0}):null,!this.checkable&&this.mergedBordered?s("div",{class:`${n}-tag__border`,style:{borderColor:z}}):null)}});export{He as N,Se as a};
