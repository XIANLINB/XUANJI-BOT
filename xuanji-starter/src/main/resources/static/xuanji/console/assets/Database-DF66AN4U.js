import{c as b,b as f,d as B,f as U,h as N,u as G,an as K,A as D,R as x,H as e,v as W,z as l,P as Q,ao as Y,a4 as J,C as c,D as u,G as p,K as L,T as E,F as q,a7 as F,S as w,W as g,Y as P,ap as M,Q as I,$ as X,U as Z,ai as tt,_ as et}from"./index-FgWOnTD7.js";import{N as j}from"./Spin-D6BRCYmd.js";import{N as R}from"./Empty-D34cG5mJ.js";import{N as rt}from"./DataTable-pwoaZuMA.js";import{N as A}from"./Tag-BaDApJwM.js";import{u as ot}from"./useFillHeight-yPAfT17J.js";import{S as at}from"./SearchOutline-qb66VXgr.js";import{_ as H}from"./Alert-BeXMGmcX.js";import{N as st}from"./Input-B8PSfsSG.js";import"./use-locale-CgRSOBYo.js";import"./Checkbox-DDgk0hrl.js";import"./RadioGroup-BV99d4RK.js";import"./get-slot-Bk_rJcZu.js";import"./Suffix-mIHTdB6s.js";import"./Select-CSQENY7x.js";import"./Checkmark-rVuvcJrV.js";const it=b("input-group",`
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
 `,[B("state-border, border",`
 border-top-right-radius: 0!important;
 border-bottom-right-radius: 0!important;
 `)]),f("&:not(:first-child)",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `,[B("state-border, border",`
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
 `),B("box-shadow, border, state-border",`
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
 `),B("box-shadow, border, state-border",`
 border-top-left-radius: 0!important;
 border-bottom-left-radius: 0!important;
 `)])])])])])]),nt={},lt=U({name:"InputGroup",props:nt,setup(d){const{mergedClsPrefixRef:h}=G(d);return K("-input-group",it,h),{mergedClsPrefix:h}},render(){const{mergedClsPrefix:d}=this;return N("div",{class:`${d}-input-group`},this.$slots)}}),ut={id:"ID",platform:"平台",status:"状态",adapter:"适配器",created_at:"创建时间",updated_at:"更新时间",instance_id:"实例ID",k:"键",scope:"作用域",v:"值",event_id:"事件ID",plugin_key:"插件标识",name:"名称",platforms:"适用平台",version:"版本",author:"作者",description:"描述",plugin_id:"插件ID",enabled:"启用",admin_password:"管理员密码",step:"向导步骤",level:"级别",module:"模块",message:"消息",bot_appid:"应用ID",bot_clientsecret:"客户端密钥",conn_mode:"连接模式",botid:"机器人ID(内部)",bot_id:"机器人ID",avatar:"头像",is_bot:"是否机器人",union_openid:"UnionID",share_url:"分享链接",welcome_msg:"欢迎语",group_id:"群号",group_name:"群名称",owner_id:"群主ID",member_count:"成员数",join_time:"加入时间",is_deleted:"已删除",member_id:"成员ID",role:"角色",nickname:"昵称",platform_user_id:"平台用户ID",remark:"备注",chat_type:"聊天类型",user_id:"用户ID",direction:"方向",msg_type:"消息类型",content:"内容",msg_id:"消息ID",msg_seq:"消息序号",raw_json:"原始数据",event_type:"事件类型",time:"时间",type:"类型",user:"用户",groupid:"群号",detail:"详情"};function dt(d){if(d==null)return"";const h=String(d).toLowerCase();return ut[h]??String(d)}const V=U({__name:"DataTable",props:{rows:{},columns:{},maxLen:{default:200},pageSize:{default:20},clickable:{type:Boolean,default:!1},loading:{type:Boolean,default:!1},emptyText:{default:"暂无数据"}},emits:["row-click"],setup(d,{emit:h}){const i=d,T=h;function _(a){if(a==null||a==="")return"";const t=String(a).trim(),m=Number(t);if(!Number.isFinite(m))return t;const O=t.length<=10?m*1e3:m,y=new Date(O);if(isNaN(y.getTime()))return t;const k=r=>String(r).padStart(2,"0");return`${y.getFullYear()}-${k(y.getMonth()+1)}-${k(y.getDate())} ${k(y.getHours())}:${k(y.getMinutes())}:${k(y.getSeconds())}`}function S(a){const t=a.toLowerCase();return t.endsWith("time")||t.includes("_time")}function z(a,t){if(t==null)return N(Q,{depth:3},()=>"—");if(a==="DIRECTION")return N(A,{size:"small",type:t==="OUT"?"success":"warning",bordered:!1},()=>t==="OUT"?"发出":"收到");if(S(a))return N("span",_(t));const m=typeof t=="object"?JSON.stringify(t):String(t);return m.length>i.maxLen?N(Y,{trigger:"hover"},{trigger:()=>N("span",{style:"cursor: help; border-bottom: 1px dotted #aaa"},m.slice(0,i.maxLen)+"…"),default:()=>m}):N("span",m)}const v=W(()=>(i.columns&&i.columns.length?i.columns:i.rows[0]?Object.keys(i.rows[0]):[]).map(t=>({title:dt(t),key:t,ellipsis:{tooltip:!0},minWidth:110,render:m=>z(t,m[t])}))),$=W(()=>i.rows.length>i.pageSize?{pageSize:i.pageSize,showSizePicker:!0,pageSizes:[20,50,100,200],showTotal:a=>`共 ${a} 条`}:!1),C=i.clickable?a=>({style:"cursor: pointer",onClick:()=>T("row-click",a)}):void 0;return(a,t)=>(l(),D("div",null,[d.loading?(l(),x(e(j),{key:0,style:{"min-height":"200px"}})):d.rows.length?(l(),x(e(rt),{key:2,columns:v.value,data:d.rows,pagination:$.value,"row-props":e(C),"max-height":i.maxHeight,size:"small",striped:"","scroll-x":1100},null,8,["columns","data","pagination","row-props","max-height"])):(l(),x(e(R),{key:1,description:d.emptyText,style:{padding:"48px 0"}},null,8,["description"]))]))}}),pt={class:"page-head"},ct={class:"page-title"},mt={class:"table-browser"},bt={class:"tb-list"},ft={class:"tb-group-title"},gt={class:"tb-tables"},ht=["onClick","title"],_t={class:"tb-name"},vt={class:"tb-src"},yt={class:"tb-data"},wt={class:"tb-data-head"},xt={class:"tb-data-title"},kt=U({__name:"Database",setup(d){const{fillHeight:h}=ot(40),i=g(!1),T=g([]),_=g(null),S=g([]),z=g([]),v=g(""),$=g(0),C=g(""),a=g(""),t=g([]);function m(r){return r==="business"?"框架级 · 业务库":r==="log"?"框架级 · 日志库":r.endsWith(":shared")?`Bot 级 · ${r.slice(0,r.indexOf(":"))} 平台共享库`:r.startsWith("qqbot:")?`Bot 级 · QQ 机器人 ${r.slice(6)}`:r.startsWith("onebot:")?`Bot 级 · OneBot ${r.slice(7)}`:r}async function O(){try{const r=await P.dbTables(),s=new Map;for(const o of r){const n=o.SOURCE||"business";s.has(n)||s.set(n,[]),s.get(n).push({name:o.TABLE_NAME,source:n})}T.value=[...s.entries()].map(([o,n])=>({key:o,label:m(o),tables:n}))}catch(r){v.value=r.message}}async function y(r,s){_.value={name:r,source:s},i.value=!0,v.value="",t.value=[],a.value="";try{const o=await P.dbRows(r,s);o.error?(v.value=o.error,S.value=[]):(S.value=o.rows||[],z.value=o.columns||[],$.value=o.count||0)}catch(o){v.value=o.message,S.value=[]}finally{i.value=!1}}async function k(){var r;if(C.value.trim()){a.value="",t.value=[];try{const s=await P.dbQuery(C.value,((r=_.value)==null?void 0:r.source)??"");s.error?a.value=s.error:t.value=s.rows||[]}catch(s){a.value=s.message}}}return J(O),(r,s)=>(l(),D("div",null,[c("div",pt,[c("div",ct,[u(e(L),{size:"20",color:"#5b5bd6"},{default:p(()=>[u(e(M))]),_:1}),s[1]||(s[1]=c("span",null,"数据库浏览",-1)),u(e(Q),{depth:"3",style:{"font-size":"13px"}},{default:p(()=>[I(w(T.value.flatMap(o=>o.tables).length)+" 张表",1)]),_:1})]),u(e(lt),{style:{width:"560px"}},{default:p(()=>[u(e(st),{value:C.value,"onUpdate:value":s[0]||(s[0]=o=>C.value=o),placeholder:"只读 SELECT 查询（作用于当前选中表所在库）",onKeyup:X(k,["enter"])},{prefix:p(()=>[u(e(L),null,{default:p(()=>[u(e(at))]),_:1})]),_:1},8,["value"]),u(e(Z),{type:"primary",loading:i.value,onClick:k},{default:p(()=>[...s[2]||(s[2]=[I("执行",-1)])]),_:1},8,["loading"])]),_:1})]),v.value?(l(),x(e(H),{key:0,type:"error",title:"查询失败",style:{"margin-bottom":"16px"}},{default:p(()=>[I(w(v.value),1)]),_:1})):E("",!0),c("div",mt,[c("div",bt,[(l(!0),D(q,null,F(T.value,o=>(l(),D("div",{key:o.key,class:"tb-group"},[c("div",ft,[u(e(A),{size:"small",bordered:!1,type:"info"},{default:p(()=>[I(w(o.tables.length),1)]),_:2},1024),c("span",null,w(o.label),1)]),c("div",gt,[(l(!0),D(q,null,F(o.tables,n=>(l(),D("button",{key:n.source+"@@"+n.name,class:tt(["tb-table",{active:_.value&&_.value.name===n.name&&_.value.source===n.source}]),onClick:St=>y(n.name,n.source),title:n.source},[u(e(L),{size:"12",style:{"flex-shrink":"0"}},{default:p(()=>[u(e(M))]),_:1}),c("span",_t,w(n.name),1),c("span",vt,w(n.source),1)],10,ht))),128))])]))),128))]),c("div",yt,[_.value?(l(),D(q,{key:0},[c("div",wt,[c("span",xt,w(_.value.name),1),u(e(Q),{depth:"3",style:{"font-size":"13px"}},{default:p(()=>[I(w($.value)+" 行",1)]),_:1})]),u(e(j),{show:i.value},{default:p(()=>[S.value.length?(l(),x(V,{key:0,rows:S.value,columns:z.value,"page-size":50,"max-height":e(h),"empty-text":"空表"},null,8,["rows","columns","max-height"])):v.value?E("",!0):(l(),x(e(R),{key:1,description:"该表暂无数据",style:{padding:"60px 0"}}))]),_:1},8,["show"])],64)):(l(),x(e(R),{key:1,description:"点击上方任意表名，查看该表全部字段和数据",style:{padding:"80px 0"}},{icon:p(()=>[u(e(L),{size:"48"},{default:p(()=>[u(e(M))]),_:1})]),_:1}))])]),a.value?(l(),x(e(H),{key:1,type:"error",title:"SQL 错误",style:{"margin-top":"12px"}},{default:p(()=>[I(w(a.value),1)]),_:1})):E("",!0),t.value.length?(l(),x(V,{key:2,style:{"margin-top":"12px"},rows:t.value,"page-size":50,"max-height":e(h),"empty-text":"无结果"},null,8,["rows","max-height"])):E("",!0)]))}}),Ut=et(kt,[["__scopeId","data-v-db0bad82"]]);export{Ut as default};
