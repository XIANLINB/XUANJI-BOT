import{c as d,a as O,b as y,d as F,i as D,e as H,f as M,h as p,g as he,u as ne,j as ve,k as re,s as xe,l as ke,p as ge,r as L,N as we,m as G,n as Q,E as be,F as q,o as ye,t as ze,q as Ce,v as T,w as J,x as z,y as X,z as C,A as j,B as v,C as Se,D as o,G as i,H as e,I as _e,J as Ie,K as P,L as A,M as Ne,O as N,P as h,Q as V,R as U,S as W,T as K,U as $e,V as B,W as Y,X as E,_ as je}from"./index-CniGiLyP.js";import{g as Me}from"./get-slot-Bk_rJcZu.js";import{F as Pe}from"./Checkmark-DgyyzcuI.js";import{u as Be}from"./use-message-DfsTWief.js";import{N as Z,a as $}from"./FormItem-B2GWc2Yt.js";import{N as R}from"./Input-DpnKKtKH.js";import{N as ee}from"./Alert-_i42cW2E.js";import{N as Re}from"./Switch-yJi5A--8.js";import{N as Oe}from"./RadioGroup-BlognMUu.js";import{N as te}from"./RadioButton-KRkbF1CJ.js";import{N as Fe}from"./Space-D95zo6Vc.js";import"./use-locale-Bn7lCNC0.js";const Te=d("steps",`
 width: 100%;
 display: flex;
`,[d("step",`
 position: relative;
 display: flex;
 flex: 1;
 `,[O("disabled","cursor: not-allowed"),O("clickable",`
 cursor: pointer;
 `),y("&:last-child",[d("step-splitor","display: none;")])]),d("step-splitor",`
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
 `,[D()]),d("icon",`
 color: var(--n-indicator-text-color);
 transition: color .3s var(--n-bezier);
 `,[D()]),d("base-icon",`
 color: var(--n-indicator-text-color);
 transition: color .3s var(--n-bezier);
 `,[D()])])]),O("vertical","flex-direction: column;",[H("show-description",[y(">",[d("step","padding-bottom: 8px;")])]),y(">",[d("step","margin-bottom: 16px;",[y("&:last-child","margin-bottom: 0;"),y(">",[d("step-indicator",[y(">",[d("step-splitor",`
 position: absolute;
 bottom: -8px;
 width: 1px;
 margin: 0 !important;
 left: calc(var(--n-indicator-size) / 2);
 height: calc(100% - var(--n-indicator-size));
 `)])]),d("step-content",[F("description","margin-top: 8px;")])])])])]),O("content-bottom",[H("vertical",[y(">",[d("step","flex-direction: column",[y(">",[d("step-line","display: flex;",[y(">",[d("step-splitor",`
 margin-top: 0;
 align-self: center;
 `)])])]),y(">",[d("step-content","margin-top: calc(var(--n-indicator-size) / 2 - var(--n-step-header-font-size) / 2);",[d("step-content-header",`
 margin-left: 0;
 `),d("step-content__description",`
 margin-left: 0;
 `)])])])])])])]);function Ae(t,l){return typeof t!="object"||t===null||Array.isArray(t)?null:(t.props||(t.props={}),t.props.internalIndex=l+1,t)}function Ue(t){return t.map((l,a)=>Ae(l,a))}const Ee=Object.assign(Object.assign({},re.props),{current:Number,status:{type:String,default:"process"},size:{type:String,default:"medium"},vertical:Boolean,contentPlacement:{type:String,default:"right"},"onUpdate:current":[Function,Array],onUpdateCurrent:[Function,Array]}),ie=ke("n-steps"),De=M({name:"Steps",props:Ee,slots:Object,setup(t,{slots:l}){const{mergedClsPrefixRef:a,mergedRtlRef:u}=ne(t),w=ve("Steps",u,a),k=re("Steps","-steps",Te,xe,t,a);return ge(ie,{props:t,mergedThemeRef:k,mergedClsPrefixRef:a,stepsSlots:l}),{mergedClsPrefix:a,rtlEnabled:w}},render(){const{mergedClsPrefix:t}=this;return p("div",{class:[`${t}-steps`,this.rtlEnabled&&`${t}-steps--rtl`,this.vertical&&`${t}-steps--vertical`,this.contentPlacement==="bottom"&&`${t}-steps--content-bottom`]},Ue(he(Me(this))))}}),Qe={status:String,title:String,description:String,disabled:Boolean,internalIndex:{type:Number,default:0}},oe=M({name:"Step",props:Qe,slots:Object,setup(t){const l=ye(ie,null);l||ze("step","`n-step` must be placed inside `n-steps`.");const{inlineThemeDisabled:a}=ne(),{props:u,mergedThemeRef:w,mergedClsPrefixRef:k,stepsSlots:_}=l,x=J(u,"vertical"),b=J(u,"contentPlacement"),S=T(()=>{const{status:c}=t;if(c)return c;{const{internalIndex:m}=t,{current:s}=u;if(s===void 0)return"process";if(m<s)return"finish";if(m===s)return u.status||"process";if(m>s)return"wait"}return"process"}),I=T(()=>{const{value:c}=S,{size:m}=u,{common:{cubicBezierEaseInOut:s},self:{stepHeaderFontWeight:n,[z("stepHeaderFontSize",m)]:f,[z("indicatorIndexFontSize",m)]:se,[z("indicatorSize",m)]:le,[z("indicatorIconSize",m)]:ae,[z("indicatorTextColor",c)]:de,[z("indicatorBorderColor",c)]:ce,[z("headerTextColor",c)]:pe,[z("splitorColor",c)]:ue,[z("indicatorColor",c)]:fe,[z("descriptionTextColor",c)]:me}}=w.value;return{"--n-bezier":s,"--n-description-text-color":me,"--n-header-text-color":pe,"--n-indicator-border-color":ce,"--n-indicator-color":fe,"--n-indicator-icon-size":ae,"--n-indicator-index-font-size":se,"--n-indicator-size":le,"--n-indicator-text-color":de,"--n-splitor-color":ue,"--n-step-header-font-size":f,"--n-step-header-font-weight":n}}),r=a?Ce("step",T(()=>{const{value:c}=S,{size:m}=u;return`${c[0]}${m[0]}`}),I,u):void 0,g=T(()=>{if(t.disabled)return;const{onUpdateCurrent:c,"onUpdate:current":m}=u;return c||m?()=>{c&&X(c,t.internalIndex),m&&X(m,t.internalIndex)}:void 0});return{stepsSlots:_,mergedClsPrefix:k,vertical:x,mergedStatus:S,handleStepClick:g,cssVars:a?void 0:I,themeClass:r==null?void 0:r.themeClass,onRender:r==null?void 0:r.onRender,contentPlacement:b}},render(){const{mergedClsPrefix:t,onRender:l,handleStepClick:a,disabled:u,contentPlacement:w,vertical:k}=this,_=L(this.$slots.default,r=>{const g=r||this.description;return g?p("div",{class:`${t}-step-content__description`},g):null}),x=p("div",{class:`${t}-step-splitor`}),b=p("div",{class:`${t}-step-indicator`,key:w},p("div",{class:`${t}-step-indicator-slot`},p(we,null,{default:()=>L(this.$slots.icon,r=>{const{mergedStatus:g,stepsSlots:c}=this;return g==="finish"||g==="error"?g==="finish"?p(G,{clsPrefix:t,key:"finish"},{default:()=>Q(c["finish-icon"],()=>[p(Pe,null)])}):g==="error"?p(G,{clsPrefix:t,key:"error"},{default:()=>Q(c["error-icon"],()=>[p(be,null)])}):null:r||p("div",{key:this.internalIndex,class:`${t}-step-indicator-slot__index`},this.internalIndex)})})),k?x:null),S=p("div",{class:`${t}-step-content`},p("div",{class:`${t}-step-content-header`},p("div",{class:`${t}-step-content-header__title`},Q(this.$slots.title,()=>[this.title])),!k&&w==="right"?x:null),_);let I;return!k&&w==="bottom"?I=p(q,null,p("div",{class:`${t}-step-line`},b,x),S):I=p(q,null,b,S),l==null||l(),p("div",{class:[`${t}-step`,u&&`${t}-step--disabled`,!u&&a&&`${t}-step--clickable`,this.themeClass,_&&`${t}-step--show-description`,`${t}-step--${this.mergedStatus}-status`],style:this.cssVars,onClick:a},I)}}),Ve={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},We=M({name:"ArrowForwardOutline",render:function(l,a){return C(),j("svg",Ve,a[0]||(a[0]=[v("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"48",d:"M268 112l144 144l-144 144"},null,-1),v("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"48",d:"M392 256H100"},null,-1)]))}}),Ke={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},He=M({name:"CheckmarkCircleOutline",render:function(l,a){return C(),j("svg",Ke,a[0]||(a[0]=[v("path",{d:"M448 256c0-106-86-192-192-192S64 150 64 256s86 192 192 192s192-86 192-192z",fill:"none",stroke:"currentColor","stroke-miterlimit":"10","stroke-width":"32"},null,-1),v("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M352 176L217.6 336L160 272"},null,-1)]))}}),Le={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Ge=M({name:"KeyOutline",render:function(l,a){return C(),j("svg",Le,a[0]||(a[0]=[v("path",{d:"M218.1 167.17c0 13 0 25.6 4.1 37.4c-43.1 50.6-156.9 184.3-167.5 194.5a20.17 20.17 0 0 0-6.7 15c0 8.5 5.2 16.7 9.6 21.3c6.6 6.9 34.8 33 40 28c15.4-15 18.5-19 24.8-25.2c9.5-9.3-1-28.3 2.3-36s6.8-9.2 12.5-10.4s15.8 2.9 23.7 3c8.3.1 12.8-3.4 19-9.2c5-4.6 8.6-8.9 8.7-15.6c.2-9-12.8-20.9-3.1-30.4s23.7 6.2 34 5s22.8-15.5 24.1-21.6s-11.7-21.8-9.7-30.7c.7-3 6.8-10 11.4-11s25 6.9 29.6 5.9c5.6-1.2 12.1-7.1 17.4-10.4c15.5 6.7 29.6 9.4 47.7 9.4c68.5 0 124-53.4 124-119.2S408.5 48 340 48s-121.9 53.37-121.9 119.17zM400 144a32 32 0 1 1-32-32a32 32 0 0 1 32 32z",fill:"none",stroke:"currentColor","stroke-linejoin":"round","stroke-width":"32"},null,-1)]))}}),qe={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Je=M({name:"OptionsOutline",render:function(l,a){return C(),j("svg",qe,a[0]||(a[0]=[Se('<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M368 128h80"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M64 128h240"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M368 384h80"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M64 384h240"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M208 256h240"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M64 256h80"></path><circle cx="336" cy="128" r="32" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"></circle><circle cx="176" cy="256" r="32" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"></circle><circle cx="336" cy="384" r="32" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"></circle>',9)]))}}),Xe={class:"setup-root"},Ye={class:"brand"},Ze={key:0,class:"step-body"},et={class:"step-head"},tt={class:"actions"},ot={key:1,class:"step-body"},nt={class:"step-head"},rt={style:{display:"flex","align-items":"center",gap:"10px"}},it={class:"actions"},st={class:"foot"},lt=M({__name:"Setup",setup(t){const l=Be(),a=Ie(),u=B(1),w=B(!1),k=B(!1),_=B(!1),x=Y({pin:"",confirm:""}),b=B("");function S(s,n){const f=s.replace(/\D/g,"").slice(0,6);x[n]=f,b.value=""}async function I(){if(!/^\d{6}$/.test(x.pin)){b.value="请输入 6 位数字口令";return}if(x.pin!==x.confirm){b.value="两次输入的口令不一致";return}w.value=!0;try{const s=await E.setupPin({pin:x.pin});if(s.error){l.error(s.error);return}u.value=2}catch(s){l.error("设置失败："+((s==null?void 0:s.message)??s))}finally{w.value=!1}}const r=Y({appId:"",clientSecret:"",sandbox:!1,connectionMethod:"websocket",domain:""});async function g(){_.value=!0;try{const s=await E.setupComplete();if(s.error){l.error(s.error);return}window.__xuanjiSetupDone=!0,l.success("初始化完成，进入控制台"),a.replace("/dashboard")}catch(s){l.error("完成失败："+((s==null?void 0:s.message)??s))}finally{_.value=!1}}async function c(){await g()}async function m(){if(!r.appId.trim()||!r.clientSecret.trim()){l.warning("请填写 AppID 与 AppSecret，或点击「跳过」");return}if(r.connectionMethod==="webhook"&&!r.domain.trim()){l.warning("Webhook 方式需填写回调域名，或点击「跳过」");return}k.value=!0;try{const s=await E.setupBot({appId:r.appId.trim(),clientSecret:r.clientSecret.trim(),sandbox:r.sandbox?"true":"false",connectionMethod:r.connectionMethod,domain:r.connectionMethod==="webhook"?r.domain.trim():""});if(s.error){l.error(s.error);return}try{await E.reloadBots(),l.success("机器人已写入并启用，正在进入控制台")}catch{l.success("机器人已写入，正在进入控制台")}await g()}catch(s){l.error("保存失败："+((s==null?void 0:s.message)??s))}finally{k.value=!1}}return(s,n)=>(C(),j("div",Xe,[n[19]||(n[19]=v("div",{class:"setup-bg"},null,-1)),o(e(_e),{class:"setup-card",bordered:!1},{default:i(()=>[v("div",Ye,[o(e(P),{size:"26",color:e(A).primary},{default:i(()=>[o(e(Ne))]),_:1},8,["color"]),n[7]||(n[7]=v("span",{class:"brand-text"},"璇玑控制台 · 初始化引导",-1))]),o(e(De),{current:u.value,class:"steps",size:"small"},{default:i(()=>[o(e(oe),{title:"设置访问口令",description:"必填"}),o(e(oe),{title:"绑定机器人",description:"可选"})]),_:1},8,["current"]),u.value===1?(C(),j("div",Ze,[v("div",et,[o(e(P),{size:"22",color:e(A).primary},{default:i(()=>[o(e(Ge))]),_:1},8,["color"]),o(e(N),{strong:"",style:{"font-size":"16px"}},{default:i(()=>[...n[8]||(n[8]=[h("设置 6 位访问口令",-1)])]),_:1})]),o(e(N),{depth:"3",class:"lead"},{default:i(()=>[...n[9]||(n[9]=[h(" 该口令用于控制台访问，系统会自动生成随机盐并使用 PBKDF2 加盐哈希后存储，原始口令不会落盘。 ",-1)])]),_:1}),o(e(Z),{class:"form"},{default:i(()=>[o(e($),{label:"访问口令（6 位数字）"},{default:i(()=>[o(e(R),{value:x.pin,"onUpdate:value":n[0]||(n[0]=f=>S(f,"pin")),type:"password","show-password-on":"click",placeholder:"例如 123456",maxlength:6,"input-props":{inputmode:"numeric"}},null,8,["value"])]),_:1}),o(e($),{label:"确认口令"},{default:i(()=>[o(e(R),{value:x.confirm,"onUpdate:value":n[1]||(n[1]=f=>S(f,"confirm")),type:"password","show-password-on":"click",placeholder:"再次输入 6 位数字",maxlength:6,"input-props":{inputmode:"numeric"}},null,8,["value"])]),_:1}),b.value?(C(),V(e(ee),{key:0,type:"error","show-icon":!0},{default:i(()=>[h(U(b.value),1)]),_:1})):W("",!0)]),_:1}),v("div",tt,[o(e(K),{type:"primary",size:"large",block:"",loading:w.value,onClick:I},{icon:i(()=>[o(e(P),null,{default:i(()=>[o(e(We))]),_:1})]),default:i(()=>[n[10]||(n[10]=h(" 下一步 ",-1))]),_:1},8,["loading"])])])):(C(),j("div",ot,[v("div",nt,[o(e(P),{size:"22",color:e(A).primary},{default:i(()=>[o(e($e))]),_:1},8,["color"]),o(e(N),{strong:"",style:{"font-size":"16px"}},{default:i(()=>[...n[11]||(n[11]=[h("绑定机器人（可选）",-1)])]),_:1})]),o(e(N),{depth:"3",class:"lead"},{default:i(()=>[...n[12]||(n[12]=[h(" 现在可以绑定一个 QQ/OneBot 机器人，也可以直接跳过，稍后在「机器人管理」页面添加。 ",-1)])]),_:1}),o(e(Z),{class:"form"},{default:i(()=>[o(e($),{label:"AppID"},{default:i(()=>[o(e(R),{value:r.appId,"onUpdate:value":n[2]||(n[2]=f=>r.appId=f),placeholder:"QQ 开放平台 AppID",clearable:""},null,8,["value"])]),_:1}),o(e($),{label:"AppSecret"},{default:i(()=>[o(e(R),{value:r.clientSecret,"onUpdate:value":n[3]||(n[3]=f=>r.clientSecret=f),type:"password","show-password-on":"click",placeholder:"QQ 开放平台 AppSecret"},null,8,["value"])]),_:1}),o(e($),{label:"环境"},{default:i(()=>[v("div",rt,[o(e(Re),{value:r.sandbox,"onUpdate:value":n[4]||(n[4]=f=>r.sandbox=f)},null,8,["value"]),o(e(N),{depth:"3",style:{"font-size":"12px"}},{default:i(()=>[h(U(r.sandbox?"沙箱环境（测试用）":"正式环境"),1)]),_:1})])]),_:1}),o(e($),{label:"连接方式"},{default:i(()=>[o(e(Oe),{value:r.connectionMethod,"onUpdate:value":n[5]||(n[5]=f=>r.connectionMethod=f)},{default:i(()=>[o(e(te),{value:"websocket"},{default:i(()=>[...n[13]||(n[13]=[h("WebSocket",-1)])]),_:1}),o(e(te),{value:"webhook"},{default:i(()=>[...n[14]||(n[14]=[h("Webhook",-1)])]),_:1})]),_:1},8,["value"])]),_:1}),r.connectionMethod==="webhook"?(C(),V(e($),{key:0,label:"回调域名"},{default:i(()=>[o(e(R),{value:r.domain,"onUpdate:value":n[6]||(n[6]=f=>r.domain=f),placeholder:"例如 xuanji.com",clearable:""},null,8,["value"])]),_:1})):W("",!0),r.connectionMethod==="webhook"&&r.domain&&r.appId?(C(),V(e($),{key:1,label:"回调地址"},{default:i(()=>[o(e(ee),{type:"info","show-icon":!1,style:{width:"100%"}},{default:i(()=>[o(e(N),{code:"",style:{"font-size":"12px"}},{default:i(()=>[h(" https://"+U(r.domain)+"/webhook/"+U(r.appId),1)]),_:1}),o(e(N),{depth:"3",style:{"font-size":"12px",display:"block","margin-top":"6px"}},{default:i(()=>[...n[15]||(n[15]=[h(" 复制此地址填到 QQ 开放平台「事件订阅」的回调地址（POST /webhook/{appId}） ",-1)])]),_:1})]),_:1})]),_:1})):W("",!0)]),_:1}),v("div",it,[o(e(Fe),{vertical:"",size:10},{default:i(()=>[o(e(K),{type:"primary",size:"large",block:"",loading:k.value,onClick:m},{default:i(()=>[...n[16]||(n[16]=[h(" 保存并进入控制台 ",-1)])]),_:1},8,["loading"]),o(e(K),{size:"large",block:"",loading:_.value,disabled:k.value,onClick:c},{icon:i(()=>[o(e(P),null,{default:i(()=>[o(e(Je))]),_:1})]),default:i(()=>[n[17]||(n[17]=h(" 跳过，直接进入控制台 ",-1))]),_:1},8,["loading","disabled"])]),_:1})])])),v("div",st,[o(e(P),{size:"14",color:e(A).success},{default:i(()=>[o(e(He))]),_:1},8,["color"]),o(e(N),{depth:"3",style:{"font-size":"12px"}},{default:i(()=>[...n[18]||(n[18]=[h("框架开发阶段 · 数据目录可随时清空重走流程",-1)])]),_:1})])]),_:1})]))}}),wt=je(lt,[["__scopeId","data-v-a3574be9"]]);export{wt as default};
