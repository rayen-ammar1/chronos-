import keycloak from "../keycloak";
import { useState, useEffect } from "react"
import {
  BarChart, Bar, LineChart, Line, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from "recharts"
import {
  Users, FileText, BarChart2, Download, TrendingUp,
  AlertTriangle, Calendar, ChevronDown, RefreshCw, CheckCircle
} from "lucide-react"


const C = {
  red:"#C62828", redDark:"#B71C1C", redLight:"#FFEBEE", redMid:"#EF9A9A",
  white:"#FFFFFF", pageBg:"#F7F7F8", border:"#E8E8E8",
  textPrimary:"#1A1A1A", textSecond:"#6B6B6B", textMuted:"#A0A0A0",
  green:"#16A34A", greenLight:"#F0FDF4", amber:"#D97706", amberLight:"#FFFBEB",
}

const MONTHS_SHORT = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
const MONTHS_FULL  = ["January","February","March","April","May","June",
                      "July","August","September","October","November","December"]

const pk  = (y,m) => `${y}-${m}`
const fmt = n => n?.toLocaleString() ?? "—"
const fmtCurrency = n => `€${n?.toLocaleString(undefined, {maximumFractionDigits:0}) ?? "—"}`

const StatCard = ({icon:Icon,value,label,sub,accent,warning}) => (
  <div style={{
    background:C.white,border:`1px solid ${C.border}`,
    borderRadius:12,padding:"18px 20px",
    display:"flex",alignItems:"flex-start",gap:14,
  }}>
    <div style={{
      width:40,height:40,borderRadius:10,flexShrink:0,
      background:warning?C.amberLight:C.redLight,
      display:"flex",alignItems:"center",justifyContent:"center",
    }}>
      <Icon size={18} color={warning?C.amber:C.red} strokeWidth={2}/>
    </div>
    <div style={{minWidth:0}}>
      <div style={{
        fontSize:22,fontWeight:800,color:accent||C.textPrimary,
        letterSpacing:"-0.5px",lineHeight:1,
      }}>{value}</div>
      <div style={{fontSize:12,fontWeight:600,color:C.textPrimary,marginTop:4}}>{label}</div>
      {sub&&<div style={{fontSize:11,color:C.textMuted,marginTop:2}}>{sub}</div>}
    </div>
  </div>
)

const ChartTip = ({active,payload,label}) => {
  if(!active||!payload?.length) return null
  return (
    <div style={{
      background:C.white,border:`1px solid ${C.border}`,borderRadius:8,
      padding:"8px 12px",fontSize:12,boxShadow:"0 4px 12px rgba(0,0,0,0.08)",
    }}>
      <div style={{color:C.textMuted,marginBottom:2}}>{label}</div>
      <div style={{fontWeight:700,color:C.textPrimary}}>{fmt(payload[0].value)} man-days</div>
    </div>
  )
}

const TrendTip = ({active,payload,label}) => {
  if(!active||!payload?.length) return null
  return (
    <div style={{
      background:C.white,border:`1px solid ${C.border}`,borderRadius:8,
      padding:"8px 12px",fontSize:12,boxShadow:"0 4px 12px rgba(0,0,0,0.08)",
    }}>
      <div style={{color:C.textMuted,marginBottom:2}}>{label}</div>
      <div style={{fontWeight:700,color:C.textPrimary}}>{fmt(payload[0].value)} man-days</div>
    </div>
  )
}

const ProductDashboard = ({ period, onPeriodChange }) => {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const pl = period.isCustom
    ? `${MONTHS_SHORT[period.startDate.m-1]} ${period.startDate.d} → ${MONTHS_SHORT[period.endDate.m-1]} ${period.endDate.d}, ${period.year}`
    : `${MONTHS_FULL[period.month-1]} ${period.year}`

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true)
      setError(null)
      try {
        // 🚀 FIX 1: Automatically refresh the token if it's older than 60 seconds!
        await keycloak.updateToken(60);
        
        const response = await fetch(
          `/api/dashboard/product?year=${period.year}&month=${period.month}`,
          {
            headers: {
              'Authorization': `Bearer ${keycloak.token}`,
              'Content-Type': 'application/json'
            }
          }
        )
        
        // If it's STILL 401 after refreshing, force the user to log in again
        if (response.status === 401) {
            keycloak.login();
            return;
        }

        if (!response.ok) throw new Error(`${response.status} ${response.statusText}`)
        const json = await response.json()
        setData(json)
      } catch (err) {
        setError(err.message)
        console.error("Failed to fetch product dashboard:", err)
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [period.year, period.month])

  // 🚀 FIX 2: Use fetch + blob for downloads because window.open() cannot send Auth headers!
  const handleDownloadReport = async () => {
    try {
      await keycloak.updateToken(60);
      const response = await fetch(`/api/reports/${period.year}_${period.month}/report`, {
        headers: { 'Authorization': `Bearer ${keycloak.token}` }
      });
      
      if (!response.ok) throw new Error("Download failed");
      
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `product_report_${period.year}_${period.month}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert("Failed to download report: " + err.message);
    }
  }

  if (loading) {
    return (
      <div style={{display:"flex",alignItems:"center",justifyContent:"center",height:"100%",color:C.textMuted}}>
        Loading product dashboard...
      </div>
    )
  }

  if (error) {
    return (
      <div style={{padding:24}}>
        <div style={{
          background:C.amberLight,border:`1px solid ${C.amber}`,
          borderRadius:12,padding:16,color:C.amber,fontSize:13,
        }}>
          ⚠️ Failed to load dashboard: {error}
        </div>
      </div>
    )
  }

  if (!data) return null

  const { statCards, manDaysByProduct, activityNatureBreakdown, topProjects, manDaysTrend, headcountByCompany } = data

  return (
    <div style={{background:C.pageBg,minHeight:"100%",padding:24}}>

      {/* ── Row 1: Stat cards ─────────────────────────────────────────────── */}
      <div style={{display:"grid",gridTemplateColumns:"repeat(4,1fr)",gap:14}}>
        <StatCard
          icon={Users}
          value={fmt(statCards.allocatedHeadcount)}
          label="Allocated Headcount"
          sub={`on products · ${pl}`}
        />
        <StatCard
          icon={FileText}
          value={fmt(statCards.totalManDays)}
          label="Total Man-Days"
          sub="booked on products"
        />
        <StatCard
          icon={TrendingUp}
          value={`${fmt(statCards.utilization)}%`}
          label="Utilization"
          sub="booked vs capacity"
        />
        <StatCard
          icon={AlertTriangle}
          value={fmt(statCards.openAnomalies)}
          label="Open Anomalies"
          sub="on my products"
          warning={statCards.openAnomalies > 0}
        />
      </div>

      {/* ── Row 2: Man-Days by Product (bar) + Actions ───────────────────── */}
      <div style={{display:"grid",gridTemplateColumns:"1fr 300px",gap:14,marginTop:14}}>

        {/* Bar: Man-Days by Product */}
        <div style={{background:C.white,border:`1px solid ${C.border}`,borderRadius:12,padding:20}}>
          <div style={{display:"flex",alignItems:"baseline",justifyContent:"space-between",marginBottom:18}}>
            <div>
              <div style={{fontSize:14,fontWeight:700,color:C.textPrimary}}>Man-Days by Product</div>
              <div style={{fontSize:12,color:C.textMuted,marginTop:2}}>Top products · {pl}</div>
            </div>
            <div style={{fontSize:12,color:C.textMuted}}>Total: {fmt(manDaysByProduct.reduce((s,c)=>s+c.manDays,0))} days</div>
          </div>
          <ResponsiveContainer width="100%" height={210}>
            <BarChart data={manDaysByProduct} barCategoryGap="36%">
              <CartesianGrid vertical={false} stroke={C.border}/>
              <XAxis dataKey="productName" tick={{fontSize:11,fill:C.textMuted}} axisLine={false} tickLine={false}/>
              <YAxis tick={{fontSize:11,fill:C.textMuted}} axisLine={false} tickLine={false}/>
              <Tooltip content={<ChartTip/>} cursor={{fill:C.redLight}}/>
              <Bar dataKey="manDays" fill={C.red} radius={[4,4,0,0]}/>
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Actions card */}
        <div style={{background:C.white,border:`1px solid ${C.border}`,borderRadius:12,padding:22,display:"flex",flexDirection:"column"}}>
          <div style={{
            width:40,height:40,borderRadius:10,background:C.redLight,
            display:"flex",alignItems:"center",justifyContent:"center",marginBottom:14,
          }}>
            <Download size={18} color={C.red}/>
          </div>
          <div style={{fontSize:14,fontWeight:700,color:C.textPrimary}}>Export Reports</div>
          <div style={{fontSize:12,color:C.textSecond,marginTop:6,lineHeight:1.65}}>
            Download the product report CSV for {pl}.
          </div>
          <div style={{display:"flex",flexDirection:"column",gap:8,marginTop:"auto",paddingTop:20}}>
            <button
              onClick={handleDownloadReport}
              style={{
                width:"100%",background:C.red,color:"white",border:"none",
                borderRadius:9,padding:"11px 0",fontSize:13,fontWeight:600,cursor:"pointer",
                display:"flex",alignItems:"center",justifyContent:"center",gap:8,
              }}
            >
              <Download size={14}/>Download Product Report CSV
            </button>
          </div>
        </div>
      </div>

      {/* ── Row 3: Activity Nature donut + Top Projects ───────────────────── */}
      <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:14,marginTop:14}}>

        {/* Donut: Activity Nature */}
        <div style={{background:C.white,border:`1px solid ${C.border}`,borderRadius:12,padding:20}}>
          <div style={{fontSize:14,fontWeight:700,color:C.textPrimary,marginBottom:4}}>Activity Nature</div>
          <div style={{fontSize:12,color:C.textMuted,marginBottom:16}}>Time distribution by type</div>
          <div style={{position:"relative",height:160}}>
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={activityNatureBreakdown}
                  cx="50%" cy="50%"
                  innerRadius={50} outerRadius={70}
                  dataKey="manDays" paddingAngle={2}
                  startAngle={90} endAngle={-270}
                >
                  {activityNatureBreakdown.map((entry,i) => (
                    <Cell key={i} fill={entry.color} strokeWidth={0}/>
                  ))}
                </Pie>
                <Tooltip formatter={(v,n,p)=>[`${fmt(v)} days`,p.payload.natureName]} contentStyle={{fontSize:12,borderRadius:8,border:`1px solid ${C.border}`}}/>
              </PieChart>
            </ResponsiveContainer>
            <div style={{
              position:"absolute",top:"50%",left:"50%",
              transform:"translate(-50%,-50%)",
              textAlign:"center",pointerEvents:"none",
            }}>
              <div style={{fontSize:18,fontWeight:800,color:C.textPrimary,lineHeight:1}}>{activityNatureBreakdown.length}</div>
              <div style={{fontSize:10,color:C.textMuted,marginTop:2}}>types</div>
            </div>
          </div>
          <div style={{display:"flex",flexDirection:"column",gap:7,marginTop:14}}>
            {activityNatureBreakdown.map(({natureName,manDays,color}) => (
              <div key={natureName} style={{display:"flex",alignItems:"center",gap:8}}>
                <div style={{width:8,height:8,borderRadius:2,background:color,flexShrink:0}}/>
                <span style={{fontSize:11,color:C.textSecond,flex:1}}>{natureName}</span>
                <span style={{fontSize:11,fontWeight:700,color:C.textPrimary}}>{fmt(manDays)} days</span>
              </div>
            ))}
          </div>
        </div>

        {/* Table: Top Projects */}
        <div style={{background:C.white,border:`1px solid ${C.border}`,borderRadius:12,padding:20}}>
          <div style={{fontSize:14,fontWeight:700,color:C.textPrimary,marginBottom:4}}>Top Projects</div>
          <div style={{fontSize:12,color:C.textMuted,marginBottom:16}}>By man-days · top 10</div>
          <div style={{overflowX:"auto"}}>
            <table style={{width:"100%",borderCollapse:"collapse",fontSize:12}}>
              <thead>
                <tr>
                  <th style={{textAlign:"left",padding:"6px 8px",color:C.textMuted,fontWeight:600,borderBottom:`1px solid ${C.border}`}}>Project</th>
                  <th style={{textAlign:"right",padding:"6px 8px",color:C.textMuted,fontWeight:600,borderBottom:`1px solid ${C.border}`}}>Man-Days</th>
                </tr>
              </thead>
              <tbody>
                {topProjects.map((row) => (
                  <tr key={row.projectName}>
                    <td style={{padding:"6px 8px",color:C.textPrimary,fontWeight:500}}>{row.projectName}</td>
                    <td style={{padding:"6px 8px",textAlign:"right",color:C.textPrimary}}>{fmt(row.manDays)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* ── Row 4: Man-Days Trend + Headcount by Company ───────────────────── */}
      <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:14,marginTop:14}}>

        {/* Line: Man-Days Trend */}
        <div style={{background:C.white,border:`1px solid ${C.border}`,borderRadius:12,padding:20}}>
          <div style={{fontSize:14,fontWeight:700,color:C.textPrimary,marginBottom:4}}>Man-Days Trend</div>
          <div style={{fontSize:12,color:C.textMuted,marginBottom:16}}>Last 6 periods</div>
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={manDaysTrend} margin={{left:-10,right:8}}>
              <CartesianGrid vertical={false} stroke={C.border}/>
              <XAxis dataKey="period" tick={{fontSize:11,fill:C.textMuted}} axisLine={false} tickLine={false}/>
              <YAxis tick={{fontSize:11,fill:C.textMuted}} axisLine={false} tickLine={false} domain={["auto","auto"]}/>
              <Tooltip content={<TrendTip/>}/>
              <Line
                type="monotone" dataKey="manDays"
                stroke={C.red} strokeWidth={2.5}
                dot={{fill:C.red,r:4,strokeWidth:0}}
                activeDot={{r:5,fill:C.red,strokeWidth:0}}
                connectNulls={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* Bar: Headcount by Company */}
        <div style={{background:C.white,border:`1px solid ${C.border}`,borderRadius:12,padding:20}}>
          <div style={{fontSize:14,fontWeight:700,color:C.textPrimary,marginBottom:4}}>Headcount by Company</div>
          <div style={{fontSize:12,color:C.textMuted,marginBottom:16}}>Employees on products</div>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={headcountByCompany} barCategoryGap="36%">
              <CartesianGrid vertical={false} stroke={C.border}/>
              <XAxis dataKey="companyName" tick={{fontSize:11,fill:C.textMuted}} axisLine={false} tickLine={false}/>
              <YAxis tick={{fontSize:11,fill:C.textMuted}} axisLine={false} tickLine={false}/>
              <Tooltip content={<ChartTip/>} cursor={{fill:C.redLight}}/>
              <Bar dataKey="headcount" fill={C.red} radius={[4,4,0,0]}/>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  )
}

export default ProductDashboard