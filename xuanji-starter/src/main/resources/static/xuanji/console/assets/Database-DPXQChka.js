import{c as b,b as f,d as L,f as U,h as S,u as G,aF as K,A as D,U as x,H as e,v as V,z as l,K as F,ax as J,ab as X,C as c,D as u,G as p,J as B,W as E,F as q,Q as A,V as w,_ as g,a0 as P,a4 as M,P as I,a2 as Y,X as Z,at as tt,a1 as et}from"./index-B04mKnMx.js";import{N as W}from"./Spin-CUBFHk6Y.js";import{N as Q,a as j}from"./Tag-CzlZN39e.js";import{N as rt}from"./DataTable-D2Hx96Yx.js";import{u as ot}from"./useFillHeight-C2WCPPVL.js";import{S as at}from"./SearchOutline-CIXbyEC9.js";import{N as H}from"./Alert-5sONNVQF.js";import{N as st}from"./Input-Ox0WOY5X.js";import"./use-locale-C67CdCFa.js";import"./Checkbox-BVlBY7Dh.js";import"./RadioGroup-BednsOXt.js";import"./get-slot-Bk_rJcZu.js";import"./Suffix-DGilw4sp.js";import"./Select-CY-oXTr3.js";import"./Checkmark-B1lqaHfC.js";import"./download-C2161hUv.js";const it=b("input-group",`
 display: inline-flex;
 width: 100%;
 flex-wrap: nowrap;
 vertical-align: bottom;
`,[f(">",[b("input",[f("&:not(:last-child)",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `),f("&:not(:first-child)",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 margin-left: -1px!important;
 `)]),b("button",[f("&:not(:last-child)",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `,[L("state-border, border",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `)]),f("&:not(:first-child)",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `,[L("state-border, border",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `)])]),f("*",[f("&:not(:last-child)",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `,[f(">",[b("input",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `),b("base-selection",[b("base-selection-label",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `),b("base-selection-tags",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `),L("box-shadow, border, state-border",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `)])])]),f("&:not(:first-child)",`
 margin-left: -1px!important;
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `,[f(">",[b("input",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `),b("base-selection",[b("base-selection-label",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `),b("base-selection-tags",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `),L("box-shadow, border, state-border",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `)])])])])])]),nt={},lt=U({name:"InputGroup",props:nt,setup(d){const{mergedClsPrefixRef:h}=G(d);return K("-input-group",it,h),{mergedClsPrefix:h}},render(){const{mergedClsPrefix:d}=this;return S("div",{class:`${d}-input-group`},this.$slots)}}),ut={id:"ID",platform:"平台",status:"状态",adapter:"适配器",created_at:"创建时间",updated_at:"更新时间",instance_id:"实例ID",k:"键",scope:"作用域",v:"值",event_id:"事件ID",plugin_key:"插件标识",name:"名称",platforms:"适用平台",version:"版本",author:"作者",description:"描述",plugin_id:"插件ID",enabled:"启用",admin_password:"管理员密码",step:"向导步骤",level:"级别",module:"模块",message:"消息",bot_appid:"应用ID",bot_clientsecret:"客户端密钥",conn_mode:"连接模式",botid:"机器人ID(内部)",bot_id:"机器人ID",avatar:"头像",is_bot:"是否机器人",union_openid:"UnionID",share_url:"分享链接",welcome_msg:"欢迎语",group_id:"群号",group_name:"群名称",owner_id:"群主ID",member_count:"成员数",join_time:"加入时间",is_deleted:"已删除",member_id:"成员ID",role:"角色",nickname:"昵称",platform_user_id:"平台用户ID",remark:"备注",chat_type:"聊天类型",user_id:"用户ID",direction:"方向",msg_type:"消息类型",content:"内容",msg_id:"消息ID",msg_seq:"消息序号",raw_json:"原始数据",event_type:"事件类型",time:"时间",type:"类型",user:"用户",groupid:"群号",detail:"详情"};function dt(d){if(d==null)return"";const h=String(d).toLowerCase();return ut[h]??String(d)}const R=U({__name:"DataTable",props:{rows:{},columns:{},maxLen:{default:200},pageSize:{default:20},clickable:{type:Boolean,default:!1},loading:{type:Boolean,default:!1},emptyText:{default:"暂无数据"}},emits:["row-click"],setup(d,{emit:h}){const i=d,T=h;function _(o){if(o==null||o==="")return"";const t=String(o).trim(),m=Number(t);if(!Number.isFinite(m))return t;const O=t.length<=10?m*1e3:m,y=new Date(O);if(isNaN(y.getTime()))return t;const k=a=>String(a).padStart(2,"0");return`${y.getFullYear()}-${k(y.getMonth()+1)}-${k(y.getDate())} ${k(y.getHours())}:${k(y.getMinutes())}:${k(y.getSeconds())}`}function N(o){const t=o.toLowerCase();return t.endsWith("time")||t.includes("_time")}function z(o,t){if(t==null)return S(F,{depth:3},()=>"—");if(o==="DIRECTION")return S(j,{size:"small",type:t==="OUT"?"success":"warning",bordered:!1},()=>t==="OUT"?"发出":"收到");if(N(o))return S("span",_(t));const m=typeof t=="object"?JSON.stringify(t):String(t);return m.length>i.maxLen?S(J,{trigger:"hover"},{trigger:()=>S("span",{style:"cursor: help; border-bottom: 1px dotted #aaa"},m.slice(0,i.maxLen)+"…"),default:()=>m}):S("span",m)}const v=V(()=>(i.columns&&i.columns.length?i.columns:i.rows[0]?Object.keys(i.rows[0]):[]).map(t=>({title:dt(t),key:t,ellipsis:{tooltip:!0},minWidth:110,render:m=>z(t,m[t])}))),$=V(()=>i.rows.length>i.pageSize?{pageSize:i.pageSize,showSizePicker:!0,pageSizes:[20,50,100,200],showTotal:o=>`共 ${o} 条`}:!1),C=i.clickable?o=>({style:"cursor: pointer",onClick:()=>T("row-click",o)}):void 0;return(o,t)=>(l(),D("div",null,[d.loading?(l(),x(e(W),{key:0,style:{"min-height":"200px"}})):d.rows.length?(l(),x(e(rt),{key:2,columns:v.value,data:d.rows,pagination:$.value,"row-props":e(C),"max-height":i.maxHeight,size:"small",striped:"","scroll-x":1100},null,8,["columns","data","pagination","row-props","max-height"])):(l(),x(e(Q),{key:1,description:d.emptyText,style:{padding:"48px 0"}},null,8,["description"]))]))}}),pt={class:"page-head"},ct={class:"page-title"},mt={class:"table-browser"},bt={class:"tb-list"},ft={class:"tb-group-title"},gt={class:"tb-tables"},ht=["onClick","title"],_t={class:"tb-name"},vt={class:"tb-src"},yt={class:"tb-data"},wt={class:"tb-data-head"},xt={class:"tb-data-title"},kt=U({__name:"Database",setup(d){const{fillHeight:h}=ot(40),i=g(!1),T=g([]),_=g(null),N=g([]),z=g([]),v=g(""),$=g(0),C=g(""),o=g(""),t=g([]);function m(a){return a==="business"?"框架级 · 业务库":a==="log"?"框架级 · 日志库":a.endsWith(":shared")?`Bot 级 · ${a.slice(0,a.indexOf(":"))} 平台共享库`:a.startsWith("qqbot:")?`Bot 级 · QQ 机器人 ${a.slice(6)}`:a}async function O(){try{const a=await P.dbTables(),s=new Map;for(const r of a){const n=r.SOURCE||"business";s.has(n)||s.set(n,[]),s.get(n).push({name:r.TABLE_NAME,source:n})}T.value=[...s.entries()].map(([r,n])=>({key:r,label:m(r),tables:n}))}catch(a){v.value=a.message}}async function y(a,s){_.value={name:a,source:s},i.value=!0,v.value="",t.value=[],o.value="";try{const r=await P.dbRows(a,s);r.error?(v.value=r.error,N.value=[]):(N.value=r.rows||[],z.value=r.columns||[],$.value=r.count||0)}catch(r){v.value=r.message,N.value=[]}finally{i.value=!1}}async function k(){var a;if(C.value.trim()){o.value="",t.value=[];try{const s=await P.dbQuery(C.value,((a=_.value)==null?void 0:a.source)??"");s.error?o.value=s.error:t.value=s.rows||[]}catch(s){o.value=s.message}}}return X(O),(a,s)=>(l(),D("div",null,[c("div",pt,[c("div",ct,[u(e(B),{size:"20",color:"#5b5bd6"},{default:p(()=>[u(e(M))]),_:1}),s[1]||(s[1]=c("span",null,"数据库浏览",-1)),u(e(F),{depth:"3",style:{"font-size":"13px"}},{default:p(()=>[I(w(T.value.flatMap(r=>r.tables).length)+" 张表",1)]),_:1})]),u(e(lt),{style:{width:"560px"}},{default:p(()=>[u(e(st),{value:C.value,"onUpdate:value":s[0]||(s[0]=r=>C.value=r),placeholder:"只读 SELECT 查询（作用于当前选中表所在库）",onKeyup:Y(k,["enter"])},{prefix:p(()=>[u(e(B),null,{default:p(()=>[u(e(at))]),_:1})]),_:1},8,["value"]),u(e(Z),{type:"primary",loading:i.value,onClick:k},{default:p(()=>[...s[2]||(s[2]=[I("执行",-1)])]),_:1},8,["loading"])]),_:1})]),v.value?(l(),x(e(H),{key:0,type:"error",title:"查询失败",style:{"margin-bottom":"16px"}},{default:p(()=>[I(w(v.value),1)]),_:1})):E("",!0),c("div",mt,[c("div",bt,[(l(!0),D(q,null,A(T.value,r=>(l(),D("div",{key:r.key,class:"tb-group"},[c("div",ft,[u(e(j),{size:"small",bordered:!1,type:"info"},{default:p(()=>[I(w(r.tables.length),1)]),_:2},1024),c("span",null,w(r.label),1)]),c("div",gt,[(l(!0),D(q,null,A(r.tables,n=>(l(),D("button",{key:n.source+"@@"+n.name,class:tt(["tb-table",{active:_.value&&_.value.name===n.name&&_.value.source===n.source}]),onClick:Nt=>y(n.name,n.source),title:n.source},[u(e(B),{size:"12",style:{"flex-shrink":"0"}},{default:p(()=>[u(e(M))]),_:1}),c("span",_t,w(n.name),1),c("span",vt,w(n.source),1)],10,ht))),128))])]))),128))]),c("div",yt,[_.value?(l(),D(q,{key:0},[c("div",wt,[c("span",xt,w(_.value.name),1),u(e(F),{depth:"3",style:{"font-size":"13px"}},{default:p(()=>[I(w($.value)+" 行",1)]),_:1})]),u(e(W),{show:i.value},{default:p(()=>[N.value.length?(l(),x(R,{key:0,rows:N.value,columns:z.value,"page-size":50,"max-height":e(h),"empty-text":"空表"},null,8,["rows","columns","max-height"])):v.value?E("",!0):(l(),x(e(Q),{key:1,description:"该表暂无数据",style:{padding:"60px 0"}}))]),_:1},8,["show"])],64)):(l(),x(e(Q),{key:1,description:"点击上方任意表名，查看该表全部字段和数据",style:{padding:"80px 0"}},{icon:p(()=>[u(e(B),{size:"48"},{default:p(()=>[u(e(M))]),_:1})]),_:1}))])]),o.value?(l(),x(e(H),{key:1,type:"error",title:"SQL 错误",style:{"margin-top":"12px"}},{default:p(()=>[I(w(o.value),1)]),_:1})):E("",!0),t.value.length?(l(),x(R,{key:2,style:{"margin-top":"12px"},rows:t.value,"page-size":50,"max-height":e(h),"empty-text":"无结果"},null,8,["rows","max-height"])):E("",!0)]))}}),Ut=et(kt,[["__scopeId","data-v-8b8ce2a9"]]);export{Ut as default};
