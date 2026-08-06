import{c as le,a as l,d as b,e as x,b as z,f as te,r as _,h as v,aG as se,u as ie,k as T,j as de,q as he,V as ge,y as be,v as B,cn as ve,x as a,aU as ue,co as I,p as ke,w as Ce,l as fe}from"./index-CniGiLyP.js";const pe={color:Object,type:{type:String,default:"default"},round:Boolean,size:String,closable:Boolean,disabled:{type:Boolean,default:void 0}},me=le("tag",`
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
`,[l("strong",`
 font-weight: var(--n-font-weight-strong);
 `),b("border",`
 pointer-events: none;
 position: absolute;
 left: 0;
 right: 0;
 top: 0;
 bottom: 0;
 border-radius: inherit;
 border: var(--n-border);
 transition: border-color .3s var(--n-bezier);
 `),b("icon",`
 display: flex;
 margin: 0 4px 0 0;
 color: var(--n-text-color);
 transition: color .3s var(--n-bezier);
 font-size: var(--n-avatar-size-override);
 `),b("avatar",`
 display: flex;
 margin: 0 6px 0 0;
 `),b("close",`
 margin: var(--n-close-margin);
 transition:
 background-color .3s var(--n-bezier),
 color .3s var(--n-bezier);
 `),l("round",`
 padding: 0 calc(var(--n-height) / 3);
 border-radius: calc(var(--n-height) / 2);
 `,[b("icon",`
 margin: 0 4px 0 calc((var(--n-height) - 8px) / -2);
 `),b("avatar",`
 margin: 0 6px 0 calc((var(--n-height) - 8px) / -2);
 `),l("closable",`
 padding: 0 calc(var(--n-height) / 4) 0 calc(var(--n-height) / 3);
 `)]),l("icon, avatar",[l("round",`
 padding: 0 calc(var(--n-height) / 3) 0 calc(var(--n-height) / 2);
 `)]),l("disabled",`
 cursor: not-allowed !important;
 opacity: var(--n-opacity-disabled);
 `),l("checkable",`
 cursor: pointer;
 box-shadow: none;
 color: var(--n-text-color-checkable);
 background-color: var(--n-color-checkable);
 `,[x("disabled",[z("&:hover","background-color: var(--n-color-hover-checkable);",[x("checked","color: var(--n-text-color-hover-checkable);")]),z("&:active","background-color: var(--n-color-pressed-checkable);",[x("checked","color: var(--n-text-color-pressed-checkable);")])]),l("checked",`
 color: var(--n-text-color-checked);
 background-color: var(--n-color-checked);
 `,[x("disabled",[z("&:hover","background-color: var(--n-color-checked-hover);"),z("&:active","background-color: var(--n-color-checked-pressed);")])])])]),xe=Object.assign(Object.assign(Object.assign({},T.props),pe),{bordered:{type:Boolean,default:void 0},checked:Boolean,checkable:Boolean,strong:Boolean,triggerClickOnClose:Boolean,onClose:[Array,Function],onMouseenter:Function,onMouseleave:Function,"onUpdate:checked":Function,onUpdateChecked:Function,internalCloseFocusable:{type:Boolean,default:!0},internalCloseIsButtonTag:{type:Boolean,default:!0},onCheckedChange:Function}),ze=fe("n-tag"),Be=te({name:"Tag",props:xe,slots:Object,setup(r){const i=ge(null),{mergedBorderedRef:o,mergedClsPrefixRef:u,inlineThemeDisabled:k,mergedRtlRef:y,mergedComponentPropsRef:d}=ie(r),h=B(()=>{var e,c;return r.size||((c=(e=d==null?void 0:d.value)===null||e===void 0?void 0:e.Tag)===null||c===void 0?void 0:c.size)||"medium"}),C=T("Tag","-tag",me,ve,r,u);ke(ze,{roundRef:Ce(r,"round")});function f(){if(!r.disabled&&r.checkable){const{checked:e,onCheckedChange:c,onUpdateChecked:s,"onUpdate:checked":n}=r;s&&s(!e),n&&n(!e),c&&c(!e)}}function p(e){if(r.triggerClickOnClose||e.stopPropagation(),!r.disabled){const{onClose:c}=r;c&&be(c,e)}}const t={setTextContent(e){const{value:c}=i;c&&(c.textContent=e)}},M=de("Tag",y,u),$=B(()=>{const{type:e,color:{color:c,textColor:s}={}}=r,n=h.value,{common:{cubicBezierEaseInOut:w},self:{padding:S,closeMargin:j,borderRadius:O,opacityDisabled:F,textColorCheckable:H,textColorHoverCheckable:N,textColorPressedCheckable:U,textColorChecked:E,colorCheckable:V,colorHoverCheckable:D,colorPressedCheckable:K,colorChecked:W,colorCheckedHover:q,colorCheckedPressed:A,closeBorderRadius:G,fontWeightStrong:L,[a("colorBordered",e)]:J,[a("closeSize",n)]:Q,[a("closeIconSize",n)]:X,[a("fontSize",n)]:Y,[a("height",n)]:R,[a("color",e)]:Z,[a("textColor",e)]:ee,[a("border",e)]:oe,[a("closeIconColor",e)]:P,[a("closeIconColorHover",e)]:re,[a("closeIconColorPressed",e)]:ce,[a("closeColorHover",e)]:ae,[a("closeColorPressed",e)]:ne}}=C.value,m=ue(j);return{"--n-font-weight-strong":L,"--n-avatar-size-override":`calc(${R} - 8px)`,"--n-bezier":w,"--n-border-radius":O,"--n-border":oe,"--n-close-icon-size":X,"--n-close-color-pressed":ne,"--n-close-color-hover":ae,"--n-close-border-radius":G,"--n-close-icon-color":P,"--n-close-icon-color-hover":re,"--n-close-icon-color-pressed":ce,"--n-close-icon-color-disabled":P,"--n-close-margin-top":m.top,"--n-close-margin-right":m.right,"--n-close-margin-bottom":m.bottom,"--n-close-margin-left":m.left,"--n-close-size":Q,"--n-color":c||(o.value?J:Z),"--n-color-checkable":V,"--n-color-checked":W,"--n-color-checked-hover":q,"--n-color-checked-pressed":A,"--n-color-hover-checkable":D,"--n-color-pressed-checkable":K,"--n-font-size":Y,"--n-height":R,"--n-opacity-disabled":F,"--n-padding":S,"--n-text-color":s||ee,"--n-text-color-checkable":H,"--n-text-color-checked":E,"--n-text-color-hover-checkable":N,"--n-text-color-pressed-checkable":U}}),g=k?he("tag",B(()=>{let e="";const{type:c,color:{color:s,textColor:n}={}}=r;return e+=c[0],e+=h.value[0],s&&(e+=`a${I(s)}`),n&&(e+=`b${I(n)}`),o.value&&(e+="c"),e}),$,r):void 0;return Object.assign(Object.assign({},t),{rtlEnabled:M,mergedClsPrefix:u,contentRef:i,mergedBordered:o,handleClick:f,handleCloseClick:p,cssVars:k?void 0:$,themeClass:g==null?void 0:g.themeClass,onRender:g==null?void 0:g.onRender})},render(){var r,i;const{mergedClsPrefix:o,rtlEnabled:u,closable:k,color:{borderColor:y}={},round:d,onRender:h,$slots:C}=this;h==null||h();const f=_(C.avatar,t=>t&&v("div",{class:`${o}-tag__avatar`},t)),p=_(C.icon,t=>t&&v("div",{class:`${o}-tag__icon`},t));return v("div",{class:[`${o}-tag`,this.themeClass,{[`${o}-tag--rtl`]:u,[`${o}-tag--strong`]:this.strong,[`${o}-tag--disabled`]:this.disabled,[`${o}-tag--checkable`]:this.checkable,[`${o}-tag--checked`]:this.checkable&&this.checked,[`${o}-tag--round`]:d,[`${o}-tag--avatar`]:f,[`${o}-tag--icon`]:p,[`${o}-tag--closable`]:k}],style:this.cssVars,onClick:this.handleClick,onMouseenter:this.onMouseenter,onMouseleave:this.onMouseleave},p||f,v("span",{class:`${o}-tag__content`,ref:"contentRef"},(i=(r=this.$slots).default)===null||i===void 0?void 0:i.call(r)),!this.checkable&&k?v(se,{clsPrefix:o,class:`${o}-tag__close`,disabled:this.disabled,onClick:this.handleCloseClick,focusable:this.internalCloseFocusable,round:d,isButtonTag:this.internalCloseIsButtonTag,absolute:!0}):null,!this.checkable&&this.mergedBordered?v("div",{class:`${o}-tag__border`,style:{borderColor:y}}):null)}});export{Be as N};
