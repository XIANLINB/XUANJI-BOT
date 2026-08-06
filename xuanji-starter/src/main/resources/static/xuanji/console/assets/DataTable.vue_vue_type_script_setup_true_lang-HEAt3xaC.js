import{b as S,c as g,ay as T,a as x,f as k,h as a,aL as D,ak as N,u as I,k as z,q as $,ao as O,v as y,V as B,aM as L,aN as j,aO as P,x as R,aP as E,A as V,Q as w,H as b,z as v,O as W,aQ as H}from"./index-CniGiLyP.js";import{N as M}from"./DataTable-CMh7A4qF.js";import{N as U}from"./Empty-kAFF6Qt4.js";import{N as q}from"./Tag-BCQAS3gm.js";const A=S([S("@keyframes spin-rotate",`
 from {
 transform: rotate(0);
 }
 to {
 transform: rotate(360deg);
 }
 `),g("spin-container",`
 position: relative;
 `,[g("spin-body",`
 position: absolute;
 top: 50%;
 left: 50%;
 transform: translateX(-50%) translateY(-50%);
 `,[T()])]),g("spin-body",`
 display: inline-flex;
 align-items: center;
 justify-content: center;
 flex-direction: column;
 `),g("spin",`
 display: inline-flex;
 height: var(--n-size);
 width: var(--n-size);
 font-size: var(--n-size);
 color: var(--n-color);
 `,[x("rotate",`
 animation: spin-rotate 2s linear infinite;
 `)]),g("spin-description",`
 display: inline-block;
 font-size: var(--n-font-size);
 color: var(--n-text-color);
 transition: color .3s var(--n-bezier);
 margin-top: 8px;
 `),g("spin-content",`
 opacity: 1;
 transition: opacity .3s var(--n-bezier);
 pointer-events: all;
 `,[x("spinning",`
 user-select: none;
 -webkit-user-select: none;
 pointer-events: none;
 opacity: var(--n-opacity-spinning);
 `)])]),F={small:20,medium:18,large:16},K=Object.assign(Object.assign(Object.assign({},z.props),{contentClass:String,contentStyle:[Object,String],description:String,size:{type:[String,Number],default:"medium"},show:{type:Boolean,default:!0},rotate:{type:Boolean,default:!0},spinning:{type:Boolean,validator:()=>!0,default:void 0},delay:Number}),L),Q=k({name:"Spin",props:K,slots:Object,setup(t){const{mergedClsPrefixRef:l,inlineThemeDisabled:e}=I(t),i=z("Spin","-spin",A,j,t,l),m=y(()=>{const{size:o}=t,{common:{cubicBezierEaseInOut:d},self:s}=i.value,{opacitySpinning:n,color:r,textColor:_}=s,p=typeof o=="number"?P(o):s[R("size",o)];return{"--n-bezier":d,"--n-opacity-spinning":n,"--n-size":p,"--n-color":r,"--n-text-color":_}}),c=e?$("spin",y(()=>{const{size:o}=t;return typeof o=="number"?String(o):o[0]}),m,t):void 0,f=E(t,["spinning","show"]),u=B(!1);return O(o=>{let d;if(f.value){const{delay:s}=t;if(s){d=window.setTimeout(()=>{u.value=!0},s),o(()=>{clearTimeout(d)});return}}u.value=f.value}),{mergedClsPrefix:l,active:u,mergedStrokeWidth:y(()=>{const{strokeWidth:o}=t;if(o!==void 0)return o;const{size:d}=t;return F[typeof d=="number"?"medium":d]}),cssVars:e?void 0:m,themeClass:c==null?void 0:c.themeClass,onRender:c==null?void 0:c.onRender}},render(){var t,l;const{$slots:e,mergedClsPrefix:i,description:m}=this,c=e.icon&&this.rotate,f=(m||e.description)&&a("div",{class:`${i}-spin-description`},m||((t=e.description)===null||t===void 0?void 0:t.call(e))),u=e.icon?a("div",{class:[`${i}-spin-body`,this.themeClass]},a("div",{class:[`${i}-spin`,c&&`${i}-spin--rotate`],style:e.default?"":this.cssVars},e.icon()),f):a("div",{class:[`${i}-spin-body`,this.themeClass]},a(D,{clsPrefix:i,style:e.default?"":this.cssVars,stroke:this.stroke,"stroke-width":this.mergedStrokeWidth,radius:this.radius,scale:this.scale,class:`${i}-spin`}),f);return(l=this.onRender)===null||l===void 0||l.call(this),e.default?a("div",{class:[`${i}-spin-container`,this.themeClass],style:this.cssVars},a("div",{class:[`${i}-spin-content`,this.active&&`${i}-spin-content--spinning`,this.contentClass],style:this.contentStyle},e),a(N,{name:"fade-in-transition"},{default:()=>this.active?u:null})):u}}),Y={id:"ID",platform:"平台",status:"状态",adapter:"适配器",created_at:"创建时间",updated_at:"更新时间",instance_id:"实例ID",k:"键",scope:"作用域",v:"值",event_id:"事件ID",plugin_key:"插件标识",name:"名称",platforms:"适用平台",version:"版本",author:"作者",description:"描述",plugin_id:"插件ID",enabled:"启用",admin_password:"管理员密码",step:"向导步骤",level:"级别",module:"模块",message:"消息",bot_appid:"应用ID",bot_clientsecret:"客户端密钥",conn_mode:"连接模式",botid:"机器人ID(内部)",bot_id:"机器人ID",avatar:"头像",is_bot:"是否机器人",union_openid:"UnionID",share_url:"分享链接",welcome_msg:"欢迎语",group_id:"群号",group_name:"群名称",owner_id:"群主ID",member_count:"成员数",join_time:"加入时间",is_deleted:"已删除",member_id:"成员ID",role:"角色",nickname:"昵称",platform_user_id:"平台用户ID",remark:"备注",chat_type:"聊天类型",user_id:"用户ID",direction:"方向",msg_type:"消息类型",content:"内容",msg_id:"消息ID",msg_seq:"消息序号",raw_json:"原始数据",event_type:"事件类型",time:"时间",type:"类型",user:"用户",groupid:"群号",detail:"详情"};function J(t){if(t==null)return"";const l=String(t).toLowerCase();return Y[l]??String(t)}const te=k({__name:"DataTable",props:{rows:{},columns:{},maxLen:{default:200},pageSize:{default:20},clickable:{type:Boolean,default:!1},loading:{type:Boolean,default:!1},emptyText:{default:"暂无数据"}},emits:["row-click"],setup(t,{emit:l}){const e=t,i=l;function m(s){if(s==null||s==="")return"";const n=String(s).trim(),r=Number(n);if(!Number.isFinite(r))return n;const _=n.length<=10?r*1e3:r,p=new Date(_);if(isNaN(p.getTime()))return n;const h=C=>String(C).padStart(2,"0");return`${p.getFullYear()}-${h(p.getMonth()+1)}-${h(p.getDate())} ${h(p.getHours())}:${h(p.getMinutes())}:${h(p.getSeconds())}`}function c(s){const n=s.toLowerCase();return n.endsWith("time")||n.includes("_time")}function f(s,n){if(n==null)return a(W,{depth:3},()=>"—");if(s==="DIRECTION")return a(q,{size:"small",type:n==="OUT"?"success":"warning",bordered:!1},()=>n==="OUT"?"发出":"收到");if(c(s))return a("span",m(n));const r=typeof n=="object"?JSON.stringify(n):String(n);return r.length>e.maxLen?a(H,{trigger:"hover"},{trigger:()=>a("span",{style:"cursor: help; border-bottom: 1px dotted #aaa"},r.slice(0,e.maxLen)+"…"),default:()=>r}):a("span",r)}const u=y(()=>(e.columns&&e.columns.length?e.columns:e.rows[0]?Object.keys(e.rows[0]):[]).map(n=>({title:J(n),key:n,ellipsis:{tooltip:!0},minWidth:110,render:r=>f(n,r[n])}))),o=y(()=>e.rows.length>e.pageSize?{pageSize:e.pageSize,showSizePicker:!0,pageSizes:[20,50,100,200],showTotal:s=>`共 ${s} 条`}:!1),d=e.clickable?s=>({style:"cursor: pointer",onClick:()=>i("row-click",s)}):void 0;return(s,n)=>(v(),V("div",null,[t.loading?(v(),w(b(Q),{key:0,style:{"min-height":"200px"}})):t.rows.length?(v(),w(b(M),{key:2,columns:u.value,data:t.rows,pagination:o.value,"row-props":b(d),"max-height":e.maxHeight,size:"small",striped:"","scroll-x":1100},null,8,["columns","data","pagination","row-props","max-height"])):(v(),w(b(U),{key:1,description:t.emptyText,style:{padding:"48px 0"}},null,8,["description"]))]))}});export{te as _};
