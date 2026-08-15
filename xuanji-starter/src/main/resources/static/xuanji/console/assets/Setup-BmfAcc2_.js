import{c as d,a as O,b as w,d as M,i as W,e as q,f as X,h as m,g as me,u as se,j as ve,k as re,s as be,l as ge,p as he,r as J,N as xe,m as Y,n as Q,E as ye,F as G,o as ze,t as we,q as ke,v as A,w as Z,x as k,y as ee,z as j,A as u,B as o,C as i,D as e,G as Se,H as R,I as S,J as _e,K as $,L as Ce,R as te,M as Ne,O as f,P as Ie,Q as $e,S as Pe,T as U,U as E,V as H,W as K,X as Re,Y as Be,Z as B,_ as oe,$ as F,a0 as Fe}from"./index-B7VD0f6B.js";import{g as Te}from"./get-slot-Bk_rJcZu.js";import{F as Ae}from"./Checkmark-DsWow6c7.js";import{A as Oe}from"./ArrowForwardOutline-BmoyAyG8.js";import{O as Me}from"./OptionsOutline-gEDHFEtT.js";import{u as je}from"./use-message-Vdhrw-Uy.js";import{N as Ue}from"./Divider-CYyokVKl.js";import{N as ne}from"./Form-CmSUfRp0.js";import{N as P}from"./FormItem-DhwD43Ez.js";import{N as T}from"./Input-fhfWDvjA.js";import{N as L}from"./Alert-Be0lFkr_.js";import{N as Ee}from"./Switch-CbPOal69.js";import{N as De}from"./RadioGroup-BdBpY1s-.js";import{N as ie}from"./RadioButton-DURh6qN0.js";import{N as Ve}from"./Space-y9F0PZ9P.js";import"./use-locale-XVmNyBth.js";import"./Suffix-CbKMMEsv.js";const We=d("steps",`
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
 `,[M("title",`
 white-space: nowrap;
 flex: 0;
 `)]),M("description",`
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
 `,[M("index",`
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
 `,[W()]),d("icon",`
 color: var(--n-indicator-text-color);
 transition: color .3s var(--n-bezier);
 `,[W()]),d("base-icon",`
 color: var(--n-indicator-text-color);
 transition: color .3s var(--n-bezier);
 `,[W()])])]),O("vertical","flex-direction: column;",[q("show-description",[w(">",[d("step","padding-bottom: 8px;")])]),w(">",[d("step","margin-bottom: 16px;",[w("&:last-child","margin-bottom: 0;"),w(">",[d("step-indicator",[w(">",[d("step-splitor",`
 position: absolute;
 bottom: -8px;
 width: 1px;
 margin: 0 !important;
 left: calc(var(--n-indicator-size) / 2);
 height: calc(100% - var(--n-indicator-size));
 `)])]),d("step-content",[M("description","margin-top: 8px;")])])])])]),O("content-bottom",[q("vertical",[w(">",[d("step","flex-direction: column",[w(">",[d("step-line","display: flex;",[w(">",[d("step-splitor",`
 margin-top: 0;
 align-self: center;
 `)])])]),w(">",[d("step-content","margin-top: calc(var(--n-indicator-size) / 2 - var(--n-step-header-font-size) / 2);",[d("step-content-header",`
 margin-left: 0;
 `),d("step-content__description",`
 margin-left: 0;
 `)])])])])])])]);function Qe(n,a){return typeof n!="object"||n===null||Array.isArray(n)?null:(n.props||(n.props={}),n.props.internalIndex=a+1,n)}function He(n){return n.map((a,g)=>Qe(a,g))}const Ke=Object.assign(Object.assign({},re.props),{current:Number,status:{type:String,default:"process"},size:{type:String,default:"medium"},vertical:Boolean,contentPlacement:{type:String,default:"right"},"onUpdate:current":[Function,Array],onUpdateCurrent:[Function,Array]}),ae=ge("n-steps"),Le=X({name:"Steps",props:Ke,slots:Object,setup(n,{slots:a}){const{mergedClsPrefixRef:g,mergedRtlRef:v}=se(n),y=ve("Steps",v,g),x=re("Steps","-steps",We,be,n,g);return he(ae,{props:n,mergedThemeRef:x,mergedClsPrefixRef:g,stepsSlots:a}),{mergedClsPrefix:g,rtlEnabled:y}},render(){const{mergedClsPrefix:n}=this;return m("div",{class:[`${n}-steps`,this.rtlEnabled&&`${n}-steps--rtl`,this.vertical&&`${n}-steps--vertical`,this.contentPlacement==="bottom"&&`${n}-steps--content-bottom`]},He(me(Te(this))))}}),Ge={status:String,title:String,description:String,disabled:Boolean,internalIndex:{type:Number,default:0}},Xe=X({name:"Step",props:Ge,slots:Object,setup(n){const a=ze(ae,null);a||we("step","`n-step` must be placed inside `n-steps`.");const{inlineThemeDisabled:g}=se(),{props:v,mergedThemeRef:y,mergedClsPrefixRef:x,stepsSlots:C}=a,p=Z(v,"vertical"),z=Z(v,"contentPlacement"),_=A(()=>{const{status:l}=n;if(l)return l;{const{internalIndex:b}=n,{current:I}=v;if(I===void 0)return"process";if(b<I)return"finish";if(b===I)return v.status||"process";if(b>I)return"wait"}return"process"}),N=A(()=>{const{value:l}=_,{size:b}=v,{common:{cubicBezierEaseInOut:I},self:{stepHeaderFontWeight:D,[k("stepHeaderFontSize",b)]:r,[k("indicatorIndexFontSize",b)]:t,[k("indicatorSize",b)]:c,[k("indicatorIconSize",b)]:V,[k("indicatorTextColor",l)]:le,[k("indicatorBorderColor",l)]:de,[k("headerTextColor",l)]:ce,[k("splitorColor",l)]:pe,[k("indicatorColor",l)]:ue,[k("descriptionTextColor",l)]:fe}}=y.value;return{"--n-bezier":I,"--n-description-text-color":fe,"--n-header-text-color":ce,"--n-indicator-border-color":de,"--n-indicator-color":ue,"--n-indicator-icon-size":V,"--n-indicator-index-font-size":t,"--n-indicator-size":c,"--n-indicator-text-color":le,"--n-splitor-color":pe,"--n-step-header-font-size":r,"--n-step-header-font-weight":D}}),h=g?ke("step",A(()=>{const{value:l}=_,{size:b}=v;return`${l[0]}${b[0]}`}),N,v):void 0,s=A(()=>{if(n.disabled)return;const{onUpdateCurrent:l,"onUpdate:current":b}=v;return l||b?()=>{l&&ee(l,n.internalIndex),b&&ee(b,n.internalIndex)}:void 0});return{stepsSlots:C,mergedClsPrefix:x,vertical:p,mergedStatus:_,handleStepClick:s,cssVars:g?void 0:N,themeClass:h==null?void 0:h.themeClass,onRender:h==null?void 0:h.onRender,contentPlacement:z}},render(){const{mergedClsPrefix:n,onRender:a,handleStepClick:g,disabled:v,contentPlacement:y,vertical:x}=this,C=J(this.$slots.default,h=>{const s=h||this.description;return s?m("div",{class:`${n}-step-content__description`},s):null}),p=m("div",{class:`${n}-step-splitor`}),z=m("div",{class:`${n}-step-indicator`,key:y},m("div",{class:`${n}-step-indicator-slot`},m(xe,null,{default:()=>J(this.$slots.icon,h=>{const{mergedStatus:s,stepsSlots:l}=this;return s==="finish"||s==="error"?s==="finish"?m(Y,{clsPrefix:n,key:"finish"},{default:()=>Q(l["finish-icon"],()=>[m(Ae,null)])}):s==="error"?m(Y,{clsPrefix:n,key:"error"},{default:()=>Q(l["error-icon"],()=>[m(ye,null)])}):null:h||m("div",{key:this.internalIndex,class:`${n}-step-indicator-slot__index`},this.internalIndex)})})),x?p:null),_=m("div",{class:`${n}-step-content`},m("div",{class:`${n}-step-content-header`},m("div",{class:`${n}-step-content-header__title`},Q(this.$slots.title,()=>[this.title])),!x&&y==="right"?p:null),C);let N;return!x&&y==="bottom"?N=m(G,null,m("div",{class:`${n}-step-line`},z,p),_):N=m(G,null,z,_),a==null||a(),m("div",{class:[`${n}-step`,v&&`${n}-step--disabled`,!v&&g&&`${n}-step--clickable`,this.themeClass,C&&`${n}-step--show-description`,`${n}-step--${this.mergedStatus}-status`],style:this.cssVars,onClick:g},N)}}),qe={class:"setup-root"},Je={class:"brand-row"},Ye={class:"brand-icon"},Ze={class:"brand-text"},et={key:0,class:"step-body"},tt={class:"step-head"},ot={class:"step-icon",style:{background:"rgba(91, 91, 214, 0.12)",color:"#5b5bd6"}},nt={class:"actions"},it={key:1,class:"step-body"},st={class:"step-head"},rt={class:"step-icon",style:{background:"rgba(32, 144, 224, 0.12)",color:"#2090e0"}},at={style:{display:"flex","align-items":"center",gap:"10px"}},lt={class:"actions"},dt={class:"foot"},ct={class:"setup-footer"},pt=X({__name:"Setup",setup(n){const a=je(),g=_e(),v=B(1),y=B(!1),x=B(!1),C=B(!1),p=oe({pin:"",confirm:""}),z=B("");function _(r,t){const c=r.replace(/\D/g,"").slice(0,6);p[t]=c,z.value=""}const N=A(()=>/^\d{6}$/.test(p.pin)&&p.pin===p.confirm);async function h(){if(!/^\d{6}$/.test(p.pin)){z.value="请输入 6 位数字口令";return}if(p.pin!==p.confirm){z.value="两次输入的口令不一致";return}y.value=!0;try{const r=await F.setupPin({pin:p.pin});if(r.error){a.error(r.error);return}v.value=2}catch(r){a.error("设置失败："+((r==null?void 0:r.message)??r))}finally{y.value=!1}}const s=oe({appId:"",clientSecret:"",sandbox:!1,connectionMethod:"websocket",domain:""});async function l(){C.value=!0;try{const r=await F.setupComplete();if(r.error){a.error(r.error);return}window.__xuanjiSetupDone=!0;try{const t=await F.authLogin({pin:p.pin});if(t.error)throw new Error(t.error)}catch{a.warning("初始化完成，但自动登录失败，请手动登录"),g.replace("/login");return}a.success("初始化完成，进入控制台"),g.replace("/dashboard")}catch(r){a.error("完成失败："+((r==null?void 0:r.message)??r))}finally{C.value=!1}}async function b(){await l()}async function I(){if(!s.appId.trim()||!s.clientSecret.trim()){a.warning("请填写 AppID 与 AppSecret，或点击「跳过」");return}if(s.connectionMethod==="webhook"&&!s.domain.trim()){a.warning("Webhook 方式需填写回调域名，或点击「跳过」");return}x.value=!0;try{const r=await F.setupBot({appId:s.appId.trim(),clientSecret:s.clientSecret.trim(),sandbox:s.sandbox?"true":"false",connectionMethod:s.connectionMethod,domain:s.connectionMethod==="webhook"?s.domain.trim():""});if(r.error){a.error(r.error);return}try{await F.reloadBots(),a.success("机器人已写入并启用，正在进入控制台")}catch{a.success("机器人已写入，正在进入控制台")}await l()}catch(r){a.error("保存失败："+((r==null?void 0:r.message)??r))}finally{x.value=!1}}const D=[{title:"设置访问口令",description:"必填"},{title:"绑定机器人",description:"可选"}];return(r,t)=>($(),j("div",qe,[t[22]||(t[22]=u("div",{class:"setup-bg"},[u("div",{class:"bg-blob bg-blob-1"}),u("div",{class:"bg-blob bg-blob-2"}),u("div",{class:"bg-blob bg-blob-3"})],-1)),o(e(Se),{class:"setup-card",bordered:!1},{default:i(()=>[u("div",Je,[u("div",Ye,[o(e(R),{size:"32",color:e(Ce).primary},{default:i(()=>[o(e(te))]),_:1},8,["color"])]),u("div",Ze,[o(e(Ne),{gradient:{deg:90,from:"#5b5bd6",to:"#2090e0"},size:20,style:{"font-weight":"800","letter-spacing":"1px"}},{default:i(()=>[...t[7]||(t[7]=[f(" 璇玑控制台 · 初始化引导 ",-1)])]),_:1}),o(e(S),{depth:"3",style:{display:"block","font-size":"12px","margin-top":"2px"}},{default:i(()=>[...t[8]||(t[8]=[f(" Xuanji Bot Framework · First-time Setup ",-1)])]),_:1})])]),o(e(Ue),{style:{margin:"14px 0 18px"}}),o(e(Le),{current:v.value,class:"steps",size:"small"},{default:i(()=>[($(),j(G,null,Ie(D,(c,V)=>o(e(Xe),{key:V,title:c.title,description:c.description},null,8,["title","description"])),64))]),_:1},8,["current"]),v.value===1?($(),j("div",et,[u("div",tt,[u("div",ot,[o(e(R),{size:"18"},{default:i(()=>[o(e($e))]),_:1})]),u("div",null,[o(e(S),{strong:"",style:{"font-size":"15px",display:"block"}},{default:i(()=>[...t[9]||(t[9]=[f("设置 6 位访问口令",-1)])]),_:1}),o(e(S),{depth:"3",style:{"font-size":"12px"}},{default:i(()=>[...t[10]||(t[10]=[f("系统自动生成随机盐 + PBKDF2 加盐哈希，原始口令不会落盘",-1)])]),_:1})])]),o(e(ne),{class:"form",onSubmit:Pe(h,["prevent"])},{default:i(()=>[o(e(P),{label:"访问口令（6 位数字）"},{default:i(()=>[o(e(T),{value:p.pin,"onUpdate:value":t[0]||(t[0]=c=>_(c,"pin")),type:"password","show-password-on":"click",placeholder:"例如 123456",maxlength:6,size:"large","input-props":{inputmode:"numeric"}},null,8,["value"])]),_:1}),o(e(P),{label:"确认口令"},{default:i(()=>[o(e(T),{value:p.confirm,"onUpdate:value":t[1]||(t[1]=c=>_(c,"confirm")),type:"password","show-password-on":"click",placeholder:"再次输入 6 位数字",maxlength:6,size:"large","input-props":{inputmode:"numeric"}},null,8,["value"])]),_:1}),z.value?($(),U(e(L),{key:0,type:"error","show-icon":!0,style:{"margin-bottom":"10px"}},{default:i(()=>[f(E(z.value),1)]),_:1})):p.confirm&&N.value?($(),U(e(L),{key:1,type:"success","show-icon":!0,style:{"margin-bottom":"10px"}},{default:i(()=>[...t[11]||(t[11]=[f(" 两次输入一致，可以继续 ",-1)])]),_:1})):H("",!0)]),_:1}),u("div",nt,[o(e(K),{type:"primary",size:"large",block:"",loading:y.value,onClick:h},{icon:i(()=>[o(e(R),null,{default:i(()=>[o(e(Oe))]),_:1})]),default:i(()=>[t[12]||(t[12]=f(" 下一步：绑定机器人 ",-1))]),_:1},8,["loading"])])])):($(),j("div",it,[u("div",st,[u("div",rt,[o(e(R),{size:"18"},{default:i(()=>[o(e(te))]),_:1})]),u("div",null,[o(e(S),{strong:"",style:{"font-size":"15px",display:"block"}},{default:i(()=>[...t[13]||(t[13]=[f("绑定机器人（可选）",-1)])]),_:1}),o(e(S),{depth:"3",style:{"font-size":"12px"}},{default:i(()=>[...t[14]||(t[14]=[f("现在绑定或跳过，稍后在「机器人管理」页面添加",-1)])]),_:1})])]),o(e(ne),{class:"form"},{default:i(()=>[o(e(P),{label:"AppID"},{default:i(()=>[o(e(T),{value:s.appId,"onUpdate:value":t[2]||(t[2]=c=>s.appId=c),placeholder:"QQ 开放平台 AppID",clearable:""},null,8,["value"])]),_:1}),o(e(P),{label:"AppSecret"},{default:i(()=>[o(e(T),{value:s.clientSecret,"onUpdate:value":t[3]||(t[3]=c=>s.clientSecret=c),type:"password","show-password-on":"click",placeholder:"QQ 开放平台 AppSecret"},null,8,["value"])]),_:1}),o(e(P),{label:"环境"},{default:i(()=>[u("div",at,[o(e(Ee),{value:s.sandbox,"onUpdate:value":t[4]||(t[4]=c=>s.sandbox=c)},null,8,["value"]),o(e(S),{depth:"3",style:{"font-size":"12px"}},{default:i(()=>[f(E(s.sandbox?"沙箱环境（测试用）":"正式环境"),1)]),_:1})])]),_:1}),o(e(P),{label:"连接方式"},{default:i(()=>[o(e(De),{value:s.connectionMethod,"onUpdate:value":t[5]||(t[5]=c=>s.connectionMethod=c)},{default:i(()=>[o(e(ie),{value:"websocket"},{default:i(()=>[...t[15]||(t[15]=[f("WebSocket",-1)])]),_:1}),o(e(ie),{value:"webhook"},{default:i(()=>[...t[16]||(t[16]=[f("Webhook",-1)])]),_:1})]),_:1},8,["value"])]),_:1}),s.connectionMethod==="webhook"?($(),U(e(P),{key:0,label:"回调域名"},{default:i(()=>[o(e(T),{value:s.domain,"onUpdate:value":t[6]||(t[6]=c=>s.domain=c),placeholder:"例如 xuanji.com",clearable:""},null,8,["value"])]),_:1})):H("",!0),s.connectionMethod==="webhook"&&s.domain&&s.appId?($(),U(e(P),{key:1,label:"回调地址"},{default:i(()=>[o(e(L),{type:"info","show-icon":!1,style:{width:"100%"}},{default:i(()=>[o(e(S),{code:"",style:{"font-size":"12px"}},{default:i(()=>[f(" https://"+E(s.domain)+"/webhook/"+E(s.appId),1)]),_:1}),o(e(S),{depth:"3",style:{"font-size":"12px",display:"block","margin-top":"6px"}},{default:i(()=>[...t[17]||(t[17]=[f(" 复制此地址填到 QQ 开放平台「事件订阅」的回调地址（POST /webhook/{appId}） ",-1)])]),_:1})]),_:1})]),_:1})):H("",!0)]),_:1}),u("div",lt,[o(e(Ve),{vertical:"",size:10},{default:i(()=>[o(e(K),{type:"primary",size:"large",block:"",loading:x.value,onClick:I},{default:i(()=>[...t[18]||(t[18]=[f(" 保存并进入控制台 ",-1)])]),_:1},8,["loading"]),o(e(K),{size:"large",block:"",loading:C.value,disabled:x.value,onClick:b},{icon:i(()=>[o(e(R),null,{default:i(()=>[o(e(Me))]),_:1})]),default:i(()=>[t[19]||(t[19]=f(" 跳过，直接进入控制台 ",-1))]),_:1},8,["loading","disabled"])]),_:1})])])),u("div",dt,[o(e(R),{size:"13",color:"#18a058"},{default:i(()=>[o(e(Re))]),_:1}),o(e(S),{depth:"3",style:{"font-size":"12px"}},{default:i(()=>[...t[20]||(t[20]=[f("框架开发阶段 · 数据目录可随时清空重走流程",-1)])]),_:1})])]),_:1}),u("div",ct,[o(e(R),{size:"12",color:"#86909c"},{default:i(()=>[o(e(Be))]),_:1}),o(e(S),{depth:"3",style:{"font-size":"11.5px"}},{default:i(()=>[...t[21]||(t[21]=[f("璇玑机器人框架 · 多平台 · Spring Boot · H2 嵌入式",-1)])]),_:1})])]))}}),$t=Fe(pt,[["__scopeId","data-v-77a36897"]]);export{$t as default};
