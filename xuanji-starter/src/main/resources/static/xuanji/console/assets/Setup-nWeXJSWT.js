import{c as d,a as O,b as w,d as A,i as Q,e as q,f as D,h,g as me,u as ie,j as he,k as se,s as ve,l as be,p as ge,r as J,N as xe,m as Y,n as H,E as ke,F as X,o as ye,t as we,q as ze,v as M,w as Z,x as z,y as ee,z as C,A as T,B as Se,C as f,D as o,G as r,H as e,I as _e,J as j,K as S,L as Ce,M as Ne,R as te,O as Ie,P as m,Q as $e,S as Pe,T as je,U,V as E,W as K,X as L,Y as Re,Z as Be,_ as R,$ as oe,a0 as B,a1 as Fe}from"./index-DzemyQKx.js";import{g as Me}from"./get-slot-Bk_rJcZu.js";import{F as Te}from"./Checkmark-3slD84Vu.js";import{A as Oe}from"./ArrowForwardOutline-BRo-q37o.js";import{u as Ae}from"./use-message-BJyZg1sC.js";import{N as Ue}from"./Divider-hAd5oXoE.js";import{N as ne}from"./Form-BgSd_euU.js";import{N as P}from"./FormItem-BOoFioNt.js";import{N as F}from"./Input-WH9GWPHm.js";import{N as G}from"./Alert-DNZdbiRN.js";import{N as Ee}from"./Switch-Cd1s-COa.js";import{N as De}from"./RadioGroup-CAE0pAMv.js";import{N as re}from"./RadioButton-DvkhCv6H.js";import{N as Ve}from"./Space-CdTRM_Ui.js";import"./use-locale-C6T7BjH6.js";import"./Suffix-DVL9o-hp.js";const We=d("steps",`
 width: 100%;
 display: flex;
`,[d("step",`
 position: relative;
 display: flex;
 flex: 1;
 `,[O("disabled","cursor: not-allowed"),O("clickable",`
 cursor: pointer;
 `),w("&:last-child",[d("step-splitor","display: none;")])]),d("step-splitor",`
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
 `),d("step-content","flex: 1;",[d("step-content-header",`
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
 `,[A("title",`
 white-space: nowrap;
 flex: 0;
 `)]),A("description",`
 color: var(--n-description-text-color);
 margin-top: 12px;
 margin-left: 9px;
 transition:
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 `)]),d("step-indicator",`
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
 `,[d("step-indicator-slot",`
 position: relative;
 width: var(--n-indicator-icon-size);
 height: var(--n-indicator-icon-size);
 font-size: var(--n-indicator-icon-size);
 line-height: var(--n-indicator-icon-size);
 `,[A("index",`
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
 `,[Q()]),d("icon",`
 color: var(--n-indicator-text-color);
 transition: color .3s var(--n-bezier);
 `,[Q()]),d("base-icon",`
 color: var(--n-indicator-text-color);
 transition: color .3s var(--n-bezier);
 `,[Q()])])]),O("vertical","flex-direction: column;",[q("show-description",[w(">",[d("step","padding-bottom: 8px;")])]),w(">",[d("step","margin-bottom: 16px;",[w("&:last-child","margin-bottom: 0;"),w(">",[d("step-indicator",[w(">",[d("step-splitor",`
 position: absolute;
 bottom: -8px;
 width: 1px;
 margin: 0 !important;
 left: calc(var(--n-indicator-size) / 2);
 height: calc(100% - var(--n-indicator-size));
 `)])]),d("step-content",[A("description","margin-top: 8px;")])])])])]),O("content-bottom",[q("vertical",[w(">",[d("step","flex-direction: column",[w(">",[d("step-line","display: flex;",[w(">",[d("step-splitor",`
 margin-top: 0;
 align-self: center;
 `)])])]),w(">",[d("step-content","margin-top: calc(var(--n-indicator-size) / 2 - var(--n-step-header-font-size) / 2);",[d("step-content-header",`
 margin-left: 0;
 `),d("step-content__description",`
 margin-left: 0;
 `)])])])])])])]);function Qe(n,a){return typeof n!="object"||n===null||Array.isArray(n)?null:(n.props||(n.props={}),n.props.internalIndex=a+1,n)}function He(n){return n.map((a,p)=>Qe(a,p))}const Ke=Object.assign(Object.assign({},se.props),{current:Number,status:{type:String,default:"process"},size:{type:String,default:"medium"},vertical:Boolean,contentPlacement:{type:String,default:"right"},"onUpdate:current":[Function,Array],onUpdateCurrent:[Function,Array]}),ae=be("n-steps"),Le=D({name:"Steps",props:Ke,slots:Object,setup(n,{slots:a}){const{mergedClsPrefixRef:p,mergedRtlRef:v}=ie(n),k=he("Steps",v,p),x=se("Steps","-steps",We,ve,n,p);return ge(ae,{props:n,mergedThemeRef:x,mergedClsPrefixRef:p,stepsSlots:a}),{mergedClsPrefix:p,rtlEnabled:k}},render(){const{mergedClsPrefix:n}=this;return h("div",{class:[`${n}-steps`,this.rtlEnabled&&`${n}-steps--rtl`,this.vertical&&`${n}-steps--vertical`,this.contentPlacement==="bottom"&&`${n}-steps--content-bottom`]},He(me(Me(this))))}}),Ge={status:String,title:String,description:String,disabled:Boolean,internalIndex:{type:Number,default:0}},Xe=D({name:"Step",props:Ge,slots:Object,setup(n){const a=ye(ae,null);a||we("step","`n-step` must be placed inside `n-steps`.");const{inlineThemeDisabled:p}=ie(),{props:v,mergedThemeRef:k,mergedClsPrefixRef:x,stepsSlots:N}=a,u=Z(v,"vertical"),y=Z(v,"contentPlacement"),_=M(()=>{const{status:l}=n;if(l)return l;{const{internalIndex:b}=n,{current:$}=v;if($===void 0)return"process";if(b<$)return"finish";if(b===$)return v.status||"process";if(b>$)return"wait"}return"process"}),I=M(()=>{const{value:l}=_,{size:b}=v,{common:{cubicBezierEaseInOut:$},self:{stepHeaderFontWeight:V,[z("stepHeaderFontSize",b)]:s,[z("indicatorIndexFontSize",b)]:t,[z("indicatorSize",b)]:c,[z("indicatorIconSize",b)]:W,[z("indicatorTextColor",l)]:le,[z("indicatorBorderColor",l)]:de,[z("headerTextColor",l)]:ce,[z("splitorColor",l)]:pe,[z("indicatorColor",l)]:ue,[z("descriptionTextColor",l)]:fe}}=k.value;return{"--n-bezier":$,"--n-description-text-color":fe,"--n-header-text-color":ce,"--n-indicator-border-color":de,"--n-indicator-color":ue,"--n-indicator-icon-size":W,"--n-indicator-index-font-size":t,"--n-indicator-size":c,"--n-indicator-text-color":le,"--n-splitor-color":pe,"--n-step-header-font-size":s,"--n-step-header-font-weight":V}}),g=p?ze("step",M(()=>{const{value:l}=_,{size:b}=v;return`${l[0]}${b[0]}`}),I,v):void 0,i=M(()=>{if(n.disabled)return;const{onUpdateCurrent:l,"onUpdate:current":b}=v;return l||b?()=>{l&&ee(l,n.internalIndex),b&&ee(b,n.internalIndex)}:void 0});return{stepsSlots:N,mergedClsPrefix:x,vertical:u,mergedStatus:_,handleStepClick:i,cssVars:p?void 0:I,themeClass:g==null?void 0:g.themeClass,onRender:g==null?void 0:g.onRender,contentPlacement:y}},render(){const{mergedClsPrefix:n,onRender:a,handleStepClick:p,disabled:v,contentPlacement:k,vertical:x}=this,N=J(this.$slots.default,g=>{const i=g||this.description;return i?h("div",{class:`${n}-step-content__description`},i):null}),u=h("div",{class:`${n}-step-splitor`}),y=h("div",{class:`${n}-step-indicator`,key:k},h("div",{class:`${n}-step-indicator-slot`},h(xe,null,{default:()=>J(this.$slots.icon,g=>{const{mergedStatus:i,stepsSlots:l}=this;return i==="finish"||i==="error"?i==="finish"?h(Y,{clsPrefix:n,key:"finish"},{default:()=>H(l["finish-icon"],()=>[h(Te,null)])}):i==="error"?h(Y,{clsPrefix:n,key:"error"},{default:()=>H(l["error-icon"],()=>[h(ke,null)])}):null:g||h("div",{key:this.internalIndex,class:`${n}-step-indicator-slot__index`},this.internalIndex)})})),x?u:null),_=h("div",{class:`${n}-step-content`},h("div",{class:`${n}-step-content-header`},h("div",{class:`${n}-step-content-header__title`},H(this.$slots.title,()=>[this.title])),!x&&k==="right"?u:null),N);let I;return!x&&k==="bottom"?I=h(X,null,h("div",{class:`${n}-step-line`},y,u),_):I=h(X,null,y,_),a==null||a(),h("div",{class:[`${n}-step`,v&&`${n}-step--disabled`,!v&&p&&`${n}-step--clickable`,this.themeClass,N&&`${n}-step--show-description`,`${n}-step--${this.mergedStatus}-status`],style:this.cssVars,onClick:p},I)}}),qe={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Je=D({name:"OptionsOutline",render:function(a,p){return C(),T("svg",qe,p[0]||(p[0]=[Se('<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M368 128h80"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M64 128h240"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M368 384h80"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M64 384h240"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M208 256h240"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M64 256h80"></path><circle cx="336" cy="128" r="32" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"></circle><circle cx="176" cy="256" r="32" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"></circle><circle cx="336" cy="384" r="32" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"></circle>',9)]))}}),Ye={class:"setup-root"},Ze={class:"brand-row"},et={class:"brand-icon"},tt={class:"brand-text"},ot={key:0,class:"step-body"},nt={class:"step-head"},rt={class:"step-icon",style:{background:"rgba(91, 91, 214, 0.12)",color:"#5b5bd6"}},it={class:"actions"},st={key:1,class:"step-body"},at={class:"step-head"},lt={class:"step-icon",style:{background:"rgba(32, 144, 224, 0.12)",color:"#2090e0"}},dt={style:{display:"flex","align-items":"center",gap:"10px"}},ct={class:"actions"},pt={class:"foot"},ut={class:"setup-footer"},ft=D({__name:"Setup",setup(n){const a=Ae(),p=Ce(),v=R(1),k=R(!1),x=R(!1),N=R(!1),u=oe({pin:"",confirm:""}),y=R("");function _(s,t){const c=s.replace(/\D/g,"").slice(0,6);u[t]=c,y.value=""}const I=M(()=>/^\d{6}$/.test(u.pin)&&u.pin===u.confirm);async function g(){if(!/^\d{6}$/.test(u.pin)){y.value="请输入 6 位数字口令";return}if(u.pin!==u.confirm){y.value="两次输入的口令不一致";return}k.value=!0;try{const s=await B.setupPin({pin:u.pin});if(s.error){a.error(s.error);return}v.value=2}catch(s){a.error("设置失败："+((s==null?void 0:s.message)??s))}finally{k.value=!1}}const i=oe({appId:"",clientSecret:"",sandbox:!1,connectionMethod:"websocket",domain:""});async function l(){N.value=!0;try{const s=await B.setupComplete();if(s.error){a.error(s.error);return}window.__xuanjiSetupDone=!0;try{const t=await B.authLogin({pin:u.pin});if(t.error)throw new Error(t.error)}catch{a.warning("初始化完成，但自动登录失败，请手动登录"),p.replace("/login");return}a.success("初始化完成，进入控制台"),p.replace("/dashboard")}catch(s){a.error("完成失败："+((s==null?void 0:s.message)??s))}finally{N.value=!1}}async function b(){await l()}async function $(){if(!i.appId.trim()||!i.clientSecret.trim()){a.warning("请填写 AppID 与 AppSecret，或点击「跳过」");return}if(i.connectionMethod==="webhook"&&!i.domain.trim()){a.warning("Webhook 方式需填写回调域名，或点击「跳过」");return}x.value=!0;try{const s=await B.setupBot({appId:i.appId.trim(),clientSecret:i.clientSecret.trim(),sandbox:i.sandbox?"true":"false",connectionMethod:i.connectionMethod,domain:i.connectionMethod==="webhook"?i.domain.trim():""});if(s.error){a.error(s.error);return}try{await B.reloadBots(),a.success("机器人已写入并启用，正在进入控制台")}catch{a.success("机器人已写入，正在进入控制台")}await l()}catch(s){a.error("保存失败："+((s==null?void 0:s.message)??s))}finally{x.value=!1}}const V=[{title:"设置访问口令",description:"必填"},{title:"绑定机器人",description:"可选"}];return(s,t)=>(C(),T("div",Ye,[t[22]||(t[22]=f("div",{class:"setup-bg"},[f("div",{class:"bg-blob bg-blob-1"}),f("div",{class:"bg-blob bg-blob-2"}),f("div",{class:"bg-blob bg-blob-3"})],-1)),o(e(_e),{class:"setup-card",bordered:!1},{default:r(()=>[f("div",Ze,[f("div",et,[o(e(j),{size:"32",color:e(Ne).primary},{default:r(()=>[o(e(te))]),_:1},8,["color"])]),f("div",tt,[o(e(Ie),{gradient:{deg:90,from:"#5b5bd6",to:"#2090e0"},size:20,style:{"font-weight":"800","letter-spacing":"1px"}},{default:r(()=>[...t[7]||(t[7]=[m(" 璇玑控制台 · 初始化引导 ",-1)])]),_:1}),o(e(S),{depth:"3",style:{display:"block","font-size":"12px","margin-top":"2px"}},{default:r(()=>[...t[8]||(t[8]=[m(" Xuanji Bot Framework · First-time Setup ",-1)])]),_:1})])]),o(e(Ue),{style:{margin:"14px 0 18px"}}),o(e(Le),{current:v.value,class:"steps",size:"small"},{default:r(()=>[(C(),T(X,null,$e(V,(c,W)=>o(e(Xe),{key:W,title:c.title,description:c.description},null,8,["title","description"])),64))]),_:1},8,["current"]),v.value===1?(C(),T("div",ot,[f("div",nt,[f("div",rt,[o(e(j),{size:"18"},{default:r(()=>[o(e(Pe))]),_:1})]),f("div",null,[o(e(S),{strong:"",style:{"font-size":"15px",display:"block"}},{default:r(()=>[...t[9]||(t[9]=[m("设置 6 位访问口令",-1)])]),_:1}),o(e(S),{depth:"3",style:{"font-size":"12px"}},{default:r(()=>[...t[10]||(t[10]=[m("系统自动生成随机盐 + PBKDF2 加盐哈希，原始口令不会落盘",-1)])]),_:1})])]),o(e(ne),{class:"form",onSubmit:je(g,["prevent"])},{default:r(()=>[o(e(P),{label:"访问口令（6 位数字）"},{default:r(()=>[o(e(F),{value:u.pin,"onUpdate:value":t[0]||(t[0]=c=>_(c,"pin")),type:"password","show-password-on":"click",placeholder:"例如 123456",maxlength:6,size:"large","input-props":{inputmode:"numeric"}},null,8,["value"])]),_:1}),o(e(P),{label:"确认口令"},{default:r(()=>[o(e(F),{value:u.confirm,"onUpdate:value":t[1]||(t[1]=c=>_(c,"confirm")),type:"password","show-password-on":"click",placeholder:"再次输入 6 位数字",maxlength:6,size:"large","input-props":{inputmode:"numeric"}},null,8,["value"])]),_:1}),y.value?(C(),U(e(G),{key:0,type:"error","show-icon":!0,style:{"margin-bottom":"10px"}},{default:r(()=>[m(E(y.value),1)]),_:1})):u.confirm&&I.value?(C(),U(e(G),{key:1,type:"success","show-icon":!0,style:{"margin-bottom":"10px"}},{default:r(()=>[...t[11]||(t[11]=[m(" 两次输入一致，可以继续 ",-1)])]),_:1})):K("",!0)]),_:1}),f("div",it,[o(e(L),{type:"primary",size:"large",block:"",loading:k.value,onClick:g},{icon:r(()=>[o(e(j),null,{default:r(()=>[o(e(Oe))]),_:1})]),default:r(()=>[t[12]||(t[12]=m(" 下一步：绑定机器人 ",-1))]),_:1},8,["loading"])])])):(C(),T("div",st,[f("div",at,[f("div",lt,[o(e(j),{size:"18"},{default:r(()=>[o(e(te))]),_:1})]),f("div",null,[o(e(S),{strong:"",style:{"font-size":"15px",display:"block"}},{default:r(()=>[...t[13]||(t[13]=[m("绑定机器人（可选）",-1)])]),_:1}),o(e(S),{depth:"3",style:{"font-size":"12px"}},{default:r(()=>[...t[14]||(t[14]=[m("现在绑定或跳过，稍后在「机器人管理」页面添加",-1)])]),_:1})])]),o(e(ne),{class:"form"},{default:r(()=>[o(e(P),{label:"AppID"},{default:r(()=>[o(e(F),{value:i.appId,"onUpdate:value":t[2]||(t[2]=c=>i.appId=c),placeholder:"QQ 开放平台 AppID",clearable:""},null,8,["value"])]),_:1}),o(e(P),{label:"AppSecret"},{default:r(()=>[o(e(F),{value:i.clientSecret,"onUpdate:value":t[3]||(t[3]=c=>i.clientSecret=c),type:"password","show-password-on":"click",placeholder:"QQ 开放平台 AppSecret"},null,8,["value"])]),_:1}),o(e(P),{label:"环境"},{default:r(()=>[f("div",dt,[o(e(Ee),{value:i.sandbox,"onUpdate:value":t[4]||(t[4]=c=>i.sandbox=c)},null,8,["value"]),o(e(S),{depth:"3",style:{"font-size":"12px"}},{default:r(()=>[m(E(i.sandbox?"沙箱环境（测试用）":"正式环境"),1)]),_:1})])]),_:1}),o(e(P),{label:"连接方式"},{default:r(()=>[o(e(De),{value:i.connectionMethod,"onUpdate:value":t[5]||(t[5]=c=>i.connectionMethod=c)},{default:r(()=>[o(e(re),{value:"websocket"},{default:r(()=>[...t[15]||(t[15]=[m("WebSocket",-1)])]),_:1}),o(e(re),{value:"webhook"},{default:r(()=>[...t[16]||(t[16]=[m("Webhook",-1)])]),_:1})]),_:1},8,["value"])]),_:1}),i.connectionMethod==="webhook"?(C(),U(e(P),{key:0,label:"回调域名"},{default:r(()=>[o(e(F),{value:i.domain,"onUpdate:value":t[6]||(t[6]=c=>i.domain=c),placeholder:"例如 xuanji.com",clearable:""},null,8,["value"])]),_:1})):K("",!0),i.connectionMethod==="webhook"&&i.domain&&i.appId?(C(),U(e(P),{key:1,label:"回调地址"},{default:r(()=>[o(e(G),{type:"info","show-icon":!1,style:{width:"100%"}},{default:r(()=>[o(e(S),{code:"",style:{"font-size":"12px"}},{default:r(()=>[m(" https://"+E(i.domain)+"/webhook/"+E(i.appId),1)]),_:1}),o(e(S),{depth:"3",style:{"font-size":"12px",display:"block","margin-top":"6px"}},{default:r(()=>[...t[17]||(t[17]=[m(" 复制此地址填到 QQ 开放平台「事件订阅」的回调地址（POST /webhook/{appId}） ",-1)])]),_:1})]),_:1})]),_:1})):K("",!0)]),_:1}),f("div",ct,[o(e(Ve),{vertical:"",size:10},{default:r(()=>[o(e(L),{type:"primary",size:"large",block:"",loading:x.value,onClick:$},{default:r(()=>[...t[18]||(t[18]=[m(" 保存并进入控制台 ",-1)])]),_:1},8,["loading"]),o(e(L),{size:"large",block:"",loading:N.value,disabled:x.value,onClick:b},{icon:r(()=>[o(e(j),null,{default:r(()=>[o(e(Je))]),_:1})]),default:r(()=>[t[19]||(t[19]=m(" 跳过，直接进入控制台 ",-1))]),_:1},8,["loading","disabled"])]),_:1})])])),f("div",pt,[o(e(j),{size:"13",color:"#18a058"},{default:r(()=>[o(e(Re))]),_:1}),o(e(S),{depth:"3",style:{"font-size":"12px"}},{default:r(()=>[...t[20]||(t[20]=[m("框架开发阶段 · 数据目录可随时清空重走流程",-1)])]),_:1})])]),_:1}),f("div",ut,[o(e(j),{size:"12",color:"#86909c"},{default:r(()=>[o(e(Be))]),_:1}),o(e(S),{depth:"3",style:{"font-size":"11.5px"}},{default:r(()=>[...t[21]||(t[21]=[m("璇玑机器人框架 · 多平台 · Spring Boot · H2 嵌入式",-1)])]),_:1})])]))}}),Pt=Fe(ft,[["__scopeId","data-v-77a36897"]]);export{Pt as default};
