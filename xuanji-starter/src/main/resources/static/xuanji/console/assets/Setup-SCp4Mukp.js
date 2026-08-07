import{c as l,a as M,b as w,d as F,i as D,e as H,f as E,h as p,g as he,u as ne,j as ve,k as re,s as ge,l as be,p as xe,r as L,N as ke,m as G,n as Q,E as we,F as q,o as ye,t as ze,q as Se,v as O,w as J,x as y,y as X,z as $,A as U,B as Ce,C as S,D as o,G as i,H as e,I as _e,J as Ie,K as P,L as T,M as Ne,O as $e,P as I,Q as v,R as V,S as A,T as W,U as K,V as Pe,W as R,X as Y,Y as j,_ as Re}from"./index-FgWOnTD7.js";import{g as je}from"./get-slot-Bk_rJcZu.js";import{F as Be}from"./Checkmark-rVuvcJrV.js";import{A as Me}from"./ArrowForwardOutline-fok0v8re.js";import{C as Fe}from"./CheckmarkCircleOutline-DmmzdxGQ.js";import{u as Oe}from"./use-message-DPdcgKLp.js";import{N as Z,a as N}from"./FormItem-DiZWumun.js";import{N as B}from"./Input-B8PSfsSG.js";import{_ as ee}from"./Alert-BeXMGmcX.js";import{N as Te}from"./Switch-DlzHmEDO.js";import{N as Ae}from"./RadioGroup-BV99d4RK.js";import{N as te}from"./RadioButton-Cb1DTl0E.js";import{N as Ue}from"./Space-DKgEX_63.js";import"./use-locale-CgRSOBYo.js";import"./Suffix-mIHTdB6s.js";const Ee=l("steps",`
 width: 100%;
 display: flex;
`,[l("step",`
 position: relative;
 display: flex;
 flex: 1;
 `,[M("disabled","cursor: not-allowed"),M("clickable",`
 cursor: pointer;
 `),w("&:last-child",[l("step-splitor","display: none;")])]),l("step-splitor",`
 background-color: var(--n-splitor-color);
 margin-top: calc(var(--n-step-header-font-size) / 2);
 height: 1px;
 flex: 1;
 align-self: flex-start;
 margin-left: 12px;
 margin-right: 12px;
 transition:
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 `),l("step-content","flex: 1;",[l("step-content-header",`
 color: var(--n-header-text-color);
 margin-top: calc(var(--n-indicator-size) / 2 - var(--n-step-header-font-size) / 2);
 line-height: var(--n-step-header-font-size);
 font-size: var(--n-step-header-font-size);
 position: relative;
 display: flex;
 font-weight: var(--n-step-header-font-weight);
 margin-left: 9px;
 transition:
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 `,[F("title",`
 white-space: nowrap;
 flex: 0;
 `)]),F("description",`
 color: var(--n-description-text-color);
 margin-top: 12px;
 margin-left: 9px;
 transition:
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 `)]),l("step-indicator",`
 background-color: var(--n-indicator-color);
 box-shadow: 0 0 0 1px var(--n-indicator-border-color);
 height: var(--n-indicator-size);
 width: var(--n-indicator-size);
 border-radius: 50%;
 display: flex;
 align-items: center;
 justify-content: center;
 transition:
 background-color .3s var(--n-bezier),
 box-shadow .3s var(--n-bezier);
 `,[l("step-indicator-slot",`
 position: relative;
 width: var(--n-indicator-icon-size);
 height: var(--n-indicator-icon-size);
 font-size: var(--n-indicator-icon-size);
 line-height: var(--n-indicator-icon-size);
 `,[F("index",`
 display: inline-block;
 text-align: center;
 position: absolute;
 left: 0;
 top: 0;
 white-space: nowrap;
 font-size: var(--n-indicator-index-font-size);
 width: var(--n-indicator-icon-size);
 height: var(--n-indicator-icon-size);
 line-height: var(--n-indicator-icon-size);
 color: var(--n-indicator-text-color);
 transition: color .3s var(--n-bezier);
 `,[D()]),l("icon",`
 color: var(--n-indicator-text-color);
 transition: color .3s var(--n-bezier);
 `,[D()]),l("base-icon",`
 color: var(--n-indicator-text-color);
 transition: color .3s var(--n-bezier);
 `,[D()])])]),M("vertical","flex-direction: column;",[H("show-description",[w(">",[l("step","padding-bottom: 8px;")])]),w(">",[l("step","margin-bottom: 16px;",[w("&:last-child","margin-bottom: 0;"),w(">",[l("step-indicator",[w(">",[l("step-splitor",`
 position: absolute;
 bottom: -8px;
 width: 1px;
 margin: 0 !important;
 left: calc(var(--n-indicator-size) / 2);
 height: calc(100% - var(--n-indicator-size));
 `)])]),l("step-content",[F("description","margin-top: 8px;")])])])])]),M("content-bottom",[H("vertical",[w(">",[l("step","flex-direction: column",[w(">",[l("step-line","display: flex;",[w(">",[l("step-splitor",`
 margin-top: 0;
 align-self: center;
 `)])])]),w(">",[l("step-content","margin-top: calc(var(--n-indicator-size) / 2 - var(--n-step-header-font-size) / 2);",[l("step-content-header",`
 margin-left: 0;
 `),l("step-content__description",`
 margin-left: 0;
 `)])])])])])])]);function De(n,a){return typeof n!="object"||n===null||Array.isArray(n)?null:(n.props||(n.props={}),n.props.internalIndex=a+1,n)}function Qe(n){return n.map((a,d)=>De(a,d))}const Ve=Object.assign(Object.assign({},re.props),{current:Number,status:{type:String,default:"process"},size:{type:String,default:"medium"},vertical:Boolean,contentPlacement:{type:String,default:"right"},"onUpdate:current":[Function,Array],onUpdateCurrent:[Function,Array]}),ie=be("n-steps"),We=E({name:"Steps",props:Ve,slots:Object,setup(n,{slots:a}){const{mergedClsPrefixRef:d,mergedRtlRef:u}=ne(n),x=ve("Steps",u,d),g=re("Steps","-steps",Ee,ge,n,d);return xe(ie,{props:n,mergedThemeRef:g,mergedClsPrefixRef:d,stepsSlots:a}),{mergedClsPrefix:d,rtlEnabled:x}},render(){const{mergedClsPrefix:n}=this;return p("div",{class:[`${n}-steps`,this.rtlEnabled&&`${n}-steps--rtl`,this.vertical&&`${n}-steps--vertical`,this.contentPlacement==="bottom"&&`${n}-steps--content-bottom`]},Qe(he(je(this))))}}),Ke={status:String,title:String,description:String,disabled:Boolean,internalIndex:{type:Number,default:0}},oe=E({name:"Step",props:Ke,slots:Object,setup(n){const a=ye(ie,null);a||ze("step","`n-step` must be placed inside `n-steps`.");const{inlineThemeDisabled:d}=ne(),{props:u,mergedThemeRef:x,mergedClsPrefixRef:g,stepsSlots:C}=a,h=J(u,"vertical"),k=J(u,"contentPlacement"),z=O(()=>{const{status:c}=n;if(c)return c;{const{internalIndex:m}=n,{current:s}=u;if(s===void 0)return"process";if(m<s)return"finish";if(m===s)return u.status||"process";if(m>s)return"wait"}return"process"}),_=O(()=>{const{value:c}=z,{size:m}=u,{common:{cubicBezierEaseInOut:s},self:{stepHeaderFontWeight:t,[y("stepHeaderFontSize",m)]:f,[y("indicatorIndexFontSize",m)]:se,[y("indicatorSize",m)]:ae,[y("indicatorIconSize",m)]:le,[y("indicatorTextColor",c)]:ce,[y("indicatorBorderColor",c)]:de,[y("headerTextColor",c)]:pe,[y("splitorColor",c)]:ue,[y("indicatorColor",c)]:fe,[y("descriptionTextColor",c)]:me}}=x.value;return{"--n-bezier":s,"--n-description-text-color":me,"--n-header-text-color":pe,"--n-indicator-border-color":de,"--n-indicator-color":fe,"--n-indicator-icon-size":le,"--n-indicator-index-font-size":se,"--n-indicator-size":ae,"--n-indicator-text-color":ce,"--n-splitor-color":ue,"--n-step-header-font-size":f,"--n-step-header-font-weight":t}}),r=d?Se("step",O(()=>{const{value:c}=z,{size:m}=u;return`${c[0]}${m[0]}`}),_,u):void 0,b=O(()=>{if(n.disabled)return;const{onUpdateCurrent:c,"onUpdate:current":m}=u;return c||m?()=>{c&&X(c,n.internalIndex),m&&X(m,n.internalIndex)}:void 0});return{stepsSlots:C,mergedClsPrefix:g,vertical:h,mergedStatus:z,handleStepClick:b,cssVars:d?void 0:_,themeClass:r==null?void 0:r.themeClass,onRender:r==null?void 0:r.onRender,contentPlacement:k}},render(){const{mergedClsPrefix:n,onRender:a,handleStepClick:d,disabled:u,contentPlacement:x,vertical:g}=this,C=L(this.$slots.default,r=>{const b=r||this.description;return b?p("div",{class:`${n}-step-content__description`},b):null}),h=p("div",{class:`${n}-step-splitor`}),k=p("div",{class:`${n}-step-indicator`,key:x},p("div",{class:`${n}-step-indicator-slot`},p(ke,null,{default:()=>L(this.$slots.icon,r=>{const{mergedStatus:b,stepsSlots:c}=this;return b==="finish"||b==="error"?b==="finish"?p(G,{clsPrefix:n,key:"finish"},{default:()=>Q(c["finish-icon"],()=>[p(Be,null)])}):b==="error"?p(G,{clsPrefix:n,key:"error"},{default:()=>Q(c["error-icon"],()=>[p(we,null)])}):null:r||p("div",{key:this.internalIndex,class:`${n}-step-indicator-slot__index`},this.internalIndex)})})),g?h:null),z=p("div",{class:`${n}-step-content`},p("div",{class:`${n}-step-content-header`},p("div",{class:`${n}-step-content-header__title`},Q(this.$slots.title,()=>[this.title])),!g&&x==="right"?h:null),C);let _;return!g&&x==="bottom"?_=p(q,null,p("div",{class:`${n}-step-line`},k,h),z):_=p(q,null,k,z),a==null||a(),p("div",{class:[`${n}-step`,u&&`${n}-step--disabled`,!u&&d&&`${n}-step--clickable`,this.themeClass,C&&`${n}-step--show-description`,`${n}-step--${this.mergedStatus}-status`],style:this.cssVars,onClick:d},_)}}),He={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Le=E({name:"OptionsOutline",render:function(a,d){return $(),U("svg",He,d[0]||(d[0]=[Ce('<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M368 128h80"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M64 128h240"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M368 384h80"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M64 384h240"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M208 256h240"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M64 256h80"></path><circle cx="336" cy="128" r="32" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"></circle><circle cx="176" cy="256" r="32" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"></circle><circle cx="336" cy="384" r="32" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"></circle>',9)]))}}),Ge={class:"setup-root"},qe={class:"brand"},Je={key:0,class:"step-body"},Xe={class:"step-head"},Ye={class:"actions"},Ze={key:1,class:"step-body"},et={class:"step-head"},tt={style:{display:"flex","align-items":"center",gap:"10px"}},ot={class:"actions"},nt={class:"foot"},rt=E({__name:"Setup",setup(n){const a=Oe(),d=Ie(),u=R(1),x=R(!1),g=R(!1),C=R(!1),h=Y({pin:"",confirm:""}),k=R("");function z(s,t){const f=s.replace(/\D/g,"").slice(0,6);h[t]=f,k.value=""}async function _(){if(!/^\d{6}$/.test(h.pin)){k.value="请输入 6 位数字口令";return}if(h.pin!==h.confirm){k.value="两次输入的口令不一致";return}x.value=!0;try{const s=await j.setupPin({pin:h.pin});if(s.error){a.error(s.error);return}u.value=2}catch(s){a.error("设置失败："+((s==null?void 0:s.message)??s))}finally{x.value=!1}}const r=Y({appId:"",clientSecret:"",sandbox:!1,connectionMethod:"websocket",domain:""});async function b(){C.value=!0;try{const s=await j.setupComplete();if(s.error){a.error(s.error);return}window.__xuanjiSetupDone=!0;try{const t=await j.authLogin({pin:h.pin});if(t.error)throw new Error(t.error)}catch{a.warning("初始化完成，但自动登录失败，请手动登录"),d.replace("/login");return}a.success("初始化完成，进入控制台"),d.replace("/dashboard")}catch(s){a.error("完成失败："+((s==null?void 0:s.message)??s))}finally{C.value=!1}}async function c(){await b()}async function m(){if(!r.appId.trim()||!r.clientSecret.trim()){a.warning("请填写 AppID 与 AppSecret，或点击「跳过」");return}if(r.connectionMethod==="webhook"&&!r.domain.trim()){a.warning("Webhook 方式需填写回调域名，或点击「跳过」");return}g.value=!0;try{const s=await j.setupBot({appId:r.appId.trim(),clientSecret:r.clientSecret.trim(),sandbox:r.sandbox?"true":"false",connectionMethod:r.connectionMethod,domain:r.connectionMethod==="webhook"?r.domain.trim():""});if(s.error){a.error(s.error);return}try{await j.reloadBots(),a.success("机器人已写入并启用，正在进入控制台")}catch{a.success("机器人已写入，正在进入控制台")}await b()}catch(s){a.error("保存失败："+((s==null?void 0:s.message)??s))}finally{g.value=!1}}return(s,t)=>($(),U("div",Ge,[t[19]||(t[19]=S("div",{class:"setup-bg"},null,-1)),o(e(_e),{class:"setup-card",bordered:!1},{default:i(()=>[S("div",qe,[o(e(P),{size:"26",color:e(T).primary},{default:i(()=>[o(e(Ne))]),_:1},8,["color"]),t[7]||(t[7]=S("span",{class:"brand-text"},"璇玑控制台 · 初始化引导",-1))]),o(e(We),{current:u.value,class:"steps",size:"small"},{default:i(()=>[o(e(oe),{title:"设置访问口令",description:"必填"}),o(e(oe),{title:"绑定机器人",description:"可选"})]),_:1},8,["current"]),u.value===1?($(),U("div",Je,[S("div",Xe,[o(e(P),{size:"22",color:e(T).primary},{default:i(()=>[o(e($e))]),_:1},8,["color"]),o(e(I),{strong:"",style:{"font-size":"16px"}},{default:i(()=>[...t[8]||(t[8]=[v("设置 6 位访问口令",-1)])]),_:1})]),o(e(I),{depth:"3",class:"lead"},{default:i(()=>[...t[9]||(t[9]=[v(" 该口令用于控制台访问，系统会自动生成随机盐并使用 PBKDF2 加盐哈希后存储，原始口令不会落盘。 ",-1)])]),_:1}),o(e(Z),{class:"form"},{default:i(()=>[o(e(N),{label:"访问口令（6 位数字）"},{default:i(()=>[o(e(B),{value:h.pin,"onUpdate:value":t[0]||(t[0]=f=>z(f,"pin")),type:"password","show-password-on":"click",placeholder:"例如 123456",maxlength:6,"input-props":{inputmode:"numeric"}},null,8,["value"])]),_:1}),o(e(N),{label:"确认口令"},{default:i(()=>[o(e(B),{value:h.confirm,"onUpdate:value":t[1]||(t[1]=f=>z(f,"confirm")),type:"password","show-password-on":"click",placeholder:"再次输入 6 位数字",maxlength:6,"input-props":{inputmode:"numeric"}},null,8,["value"])]),_:1}),k.value?($(),V(e(ee),{key:0,type:"error","show-icon":!0},{default:i(()=>[v(A(k.value),1)]),_:1})):W("",!0)]),_:1}),S("div",Ye,[o(e(K),{type:"primary",size:"large",block:"",loading:x.value,onClick:_},{icon:i(()=>[o(e(P),null,{default:i(()=>[o(e(Me))]),_:1})]),default:i(()=>[t[10]||(t[10]=v(" 下一步 ",-1))]),_:1},8,["loading"])])])):($(),U("div",Ze,[S("div",et,[o(e(P),{size:"22",color:e(T).primary},{default:i(()=>[o(e(Pe))]),_:1},8,["color"]),o(e(I),{strong:"",style:{"font-size":"16px"}},{default:i(()=>[...t[11]||(t[11]=[v("绑定机器人（可选）",-1)])]),_:1})]),o(e(I),{depth:"3",class:"lead"},{default:i(()=>[...t[12]||(t[12]=[v(" 现在可以绑定一个 QQ/OneBot 机器人，也可以直接跳过，稍后在「机器人管理」页面添加。 ",-1)])]),_:1}),o(e(Z),{class:"form"},{default:i(()=>[o(e(N),{label:"AppID"},{default:i(()=>[o(e(B),{value:r.appId,"onUpdate:value":t[2]||(t[2]=f=>r.appId=f),placeholder:"QQ 开放平台 AppID",clearable:""},null,8,["value"])]),_:1}),o(e(N),{label:"AppSecret"},{default:i(()=>[o(e(B),{value:r.clientSecret,"onUpdate:value":t[3]||(t[3]=f=>r.clientSecret=f),type:"password","show-password-on":"click",placeholder:"QQ 开放平台 AppSecret"},null,8,["value"])]),_:1}),o(e(N),{label:"环境"},{default:i(()=>[S("div",tt,[o(e(Te),{value:r.sandbox,"onUpdate:value":t[4]||(t[4]=f=>r.sandbox=f)},null,8,["value"]),o(e(I),{depth:"3",style:{"font-size":"12px"}},{default:i(()=>[v(A(r.sandbox?"沙箱环境（测试用）":"正式环境"),1)]),_:1})])]),_:1}),o(e(N),{label:"连接方式"},{default:i(()=>[o(e(Ae),{value:r.connectionMethod,"onUpdate:value":t[5]||(t[5]=f=>r.connectionMethod=f)},{default:i(()=>[o(e(te),{value:"websocket"},{default:i(()=>[...t[13]||(t[13]=[v("WebSocket",-1)])]),_:1}),o(e(te),{value:"webhook"},{default:i(()=>[...t[14]||(t[14]=[v("Webhook",-1)])]),_:1})]),_:1},8,["value"])]),_:1}),r.connectionMethod==="webhook"?($(),V(e(N),{key:0,label:"回调域名"},{default:i(()=>[o(e(B),{value:r.domain,"onUpdate:value":t[6]||(t[6]=f=>r.domain=f),placeholder:"例如 xuanji.com",clearable:""},null,8,["value"])]),_:1})):W("",!0),r.connectionMethod==="webhook"&&r.domain&&r.appId?($(),V(e(N),{key:1,label:"回调地址"},{default:i(()=>[o(e(ee),{type:"info","show-icon":!1,style:{width:"100%"}},{default:i(()=>[o(e(I),{code:"",style:{"font-size":"12px"}},{default:i(()=>[v(" https://"+A(r.domain)+"/webhook/"+A(r.appId),1)]),_:1}),o(e(I),{depth:"3",style:{"font-size":"12px",display:"block","margin-top":"6px"}},{default:i(()=>[...t[15]||(t[15]=[v(" 复制此地址填到 QQ 开放平台「事件订阅」的回调地址（POST /webhook/{appId}） ",-1)])]),_:1})]),_:1})]),_:1})):W("",!0)]),_:1}),S("div",ot,[o(e(Ue),{vertical:"",size:10},{default:i(()=>[o(e(K),{type:"primary",size:"large",block:"",loading:g.value,onClick:m},{default:i(()=>[...t[16]||(t[16]=[v(" 保存并进入控制台 ",-1)])]),_:1},8,["loading"]),o(e(K),{size:"large",block:"",loading:C.value,disabled:g.value,onClick:c},{icon:i(()=>[o(e(P),null,{default:i(()=>[o(e(Le))]),_:1})]),default:i(()=>[t[17]||(t[17]=v(" 跳过，直接进入控制台 ",-1))]),_:1},8,["loading","disabled"])]),_:1})])])),S("div",nt,[o(e(P),{size:"14",color:e(T).success},{default:i(()=>[o(e(Fe))]),_:1},8,["color"]),o(e(I),{depth:"3",style:{"font-size":"12px"}},{default:i(()=>[...t[18]||(t[18]=[v("框架开发阶段 · 数据目录可随时清空重走流程",-1)])]),_:1})])]),_:1})]))}}),kt=Re(rt,[["__scopeId","data-v-a8cbad84"]]);export{kt as default};
