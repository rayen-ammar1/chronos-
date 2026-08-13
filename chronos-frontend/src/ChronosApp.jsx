import RecommendationsCard from "./components/RecommendationsCard"
import { useState, useRef, useEffect } from "react"
import {
  BarChart, Bar, LineChart, Line, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from "recharts"
import {
  Clock, Users, FileText, BarChart2, Settings, LogOut,
  Bell, ChevronLeft, ChevronRight, Download, CheckCircle,
  AlertTriangle, ChevronDown, TrendingUp, RefreshCw, Calendar
} from "lucide-react"
import keycloak from './keycloak'
import FinancialDashboard from './components/FinancialDashboard'
import ProductDashboard from './components/ProductDashboard'

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

// ── Calendar helpers ──────────────────────────────────────────────────────────
const daysInMonth   = (y, m) => new Date(y, m, 0).getDate()
const firstDayOfMonth = (y, m) => new Date(y, m - 1, 1).getDay()
const dateNum = (y, m, d) => y * 10000 + m * 100 + d
const dateLabel = (d) => d ? `${MONTHS_SHORT[d.m-1]} ${d.d}, ${d.y}` : ""

const Divider = ({mt=16,mb=16}) => (
  <div style={{height:1,background:C.border,margin:`${mt}px 0 ${mb}px`}}/>
)

const Logo = ({variant="dark"}) => (
  <div style={{display:"flex",alignItems:"center",gap:9}}>
    <div style={{
      width:34,height:34,borderRadius:9,flexShrink:0,
      background:variant==="light"?"rgba(255,255,255,0.18)":C.red,
      display:"flex",alignItems:"center",justifyContent:"center",
    }}>
      <Clock size={17} color="white" strokeWidth={2.2}/>
    </div>
    <span style={{
      fontSize:17,fontWeight:800,letterSpacing:"-0.4px",
      color:variant==="light"?"white":C.textPrimary,
    }}>CHRONOS</span>
  </div>
)

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
      <div style={{fontWeight:700,color:C.textPrimary}}>{fmt(payload[0].value)} members</div>
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
      {payload[0].value!=null
        ?<div style={{fontWeight:700,color:C.textPrimary}}>{fmt(payload[0].value)} lines</div>
        :<div style={{color:C.textMuted}}>Not generated</div>
      }
    </div>
  )
}

const AnomalyTip = ({active,payload,label}) => {
  if(!active||!payload?.length) return null
  return (
    <div style={{
      background:C.white,border:`1px solid ${C.border}`,borderRadius:8,
      padding:"8px 12px",fontSize:12,boxShadow:"0 4px 12px rgba(0,0,0,0.08)",
    }}>
      <div style={{color:C.textMuted,marginBottom:2}}>{label}</div>
      <div style={{fontWeight:700,color:C.red}}>{payload[0].value} anomalies</div>
    </div>
  )
}

// ── Date range picker ────────────────────────────────────────────────────────
const DateRangePicker = ({ startDate, endDate, onStartChange, onEndChange }) => {
  const [viewYear,  setViewYear]  = useState(2024)
  const [viewMonth, setViewMonth] = useState(7)
  const [hoverDate, setHoverDate] = useState(null)
  const [phase,     setPhase]     = useState("start")

  const handleDayClick = (y, m, d) => {
    if (phase === "start") {
      onStartChange({ y, m, d })
      onEndChange(null)
      setPhase("end")
    } else {
      const clickNum = dateNum(y, m, d)
      const startNum = startDate ? dateNum(startDate.y, startDate.m, startDate.d) : 0
      if (startDate && clickNum < startNum) {
        onEndChange(startDate)
        onStartChange({ y, m, d })
      } else {
        onEndChange({ y, m, d })
      }
      setPhase("start")
    }
  }

  const prevMonth = () => {
    if (viewMonth === 1) { setViewYear(y => y-1); setViewMonth(12) }
    else setViewMonth(m => m-1)
  }
  const nextMonth = () => {
    if (viewMonth === 12) { setViewYear(y => y+1); setViewMonth(1) }
    else setViewMonth(m => m+1)
  }

  const numDays  = daysInMonth(viewYear, viewMonth)
  const firstDay = firstDayOfMonth(viewYear, viewMonth)
  const cells    = []
  for (let i = 0; i < firstDay; i++) cells.push(null)
  for (let d = 1; d <= numDays; d++) cells.push(d)
  while (cells.length % 7 !== 0) cells.push(null)

  const effectiveEnd = endDate || (startDate && hoverDate ? hoverDate : null)

  const dayState = (d) => {
    const isStart = startDate && startDate.y===viewYear && startDate.m===viewMonth && startDate.d===d
    const isEnd   = endDate   && endDate.y===viewYear   && endDate.m===viewMonth   && endDate.d===d
    let   inRange = false
    if (startDate && effectiveEnd) {
      const num  = dateNum(viewYear, viewMonth, d)
      const sNum = dateNum(startDate.y, startDate.m, startDate.d)
      const eNum = dateNum(effectiveEnd.y, effectiveEnd.m, effectiveEnd.d)
      const lo   = Math.min(sNum, eNum)
      const hi   = Math.max(sNum, eNum)
      inRange    = num > lo && num < hi
    }
    return { isStart, isEnd, inRange }
  }

  return (
    <div>
      <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",marginBottom:14}}>
        <button
          onClick={prevMonth}
          style={{background:"none",border:`1px solid ${C.border}`,borderRadius:8,cursor:"pointer",
            width:32,height:32,display:"flex",alignItems:"center",justifyContent:"center"}}
        ><ChevronLeft size={15} color={C.textSecond}/></button>
        <span style={{fontSize:14,fontWeight:700,color:C.textPrimary}}>
          {MONTHS_FULL[viewMonth-1]} {viewYear}
        </span>
        <button
          onClick={nextMonth}
          style={{background:"none",border:`1px solid ${C.border}`,borderRadius:8,cursor:"pointer",
            width:32,height:32,display:"flex",alignItems:"center",justifyContent:"center"}}
        ><ChevronRight size={15} color={C.textSecond}/></button>
      </div>

      <div style={{display:"grid",gridTemplateColumns:"repeat(7,1fr)",marginBottom:4}}>
        {["Su","Mo","Tu","We","Th","Fr","Sa"].map(d => (
          <div key={d} style={{textAlign:"center",fontSize:10,fontWeight:600,
            color:C.textMuted,padding:"4px 0",letterSpacing:"0.03em"}}>{d}</div>
        ))}
      </div>

      <div style={{display:"grid",gridTemplateColumns:"repeat(7,1fr)",gap:2}}>
        {cells.map((day, i) => {
          if (day === null) return <div key={`e${i}`}/>
          const { isStart, isEnd, inRange } = dayState(day)
          const highlighted = isStart || isEnd
          return (
            <div
              key={day}
              onClick={() => handleDayClick(viewYear, viewMonth, day)}
              onMouseEnter={() => {
                if (!endDate && startDate) setHoverDate({y:viewYear,m:viewMonth,d:day})
              }}
              onMouseLeave={() => setHoverDate(null)}
              style={{
                textAlign:"center", padding:"7px 2px", fontSize:12,
                cursor:"pointer", borderRadius:6, userSelect:"none",
                background: highlighted ? C.red : inRange ? C.redLight : "transparent",
                color: highlighted ? "white" : inRange ? C.red : C.textPrimary,
                fontWeight: highlighted ? 700 : 400,
                transition:"background 0.1s",
              }}
            >{day}</div>
          )
        })}
      </div>

      <div style={{
        marginTop:14, padding:"8px 12px", borderRadius:8,
        background: phase==="start" ? C.redLight : "#FFF8E1",
        fontSize:12, color: phase==="start" ? C.red : C.amber,
        textAlign:"center", fontWeight:500,
      }}>
        {phase==="start"
          ? "① Click to set the start date"
          : `② Start: ${dateLabel(startDate)} — click to set the end date`
        }
      </div>

      {startDate && endDate && (
        <div style={{
          marginTop:10,display:"flex",alignItems:"center",justifyContent:"center",
          gap:8, fontSize:12, color:C.textPrimary, fontWeight:600,
        }}>
          <span style={{
            background:C.redLight, color:C.red, padding:"4px 10px",
            borderRadius:6, fontSize:12,
          }}>{dateLabel(startDate)}</span>
          <span style={{color:C.textMuted}}>→</span>
          <span style={{
            background:C.redLight, color:C.red, padding:"4px 10px",
            borderRadius:6, fontSize:12,
          }}>{dateLabel(endDate)}</span>
          <button
            onClick={() => { onStartChange(null); onEndChange(null); setPhase("start") }}
            style={{
              background:"none", border:"none", cursor:"pointer",
              color:C.textMuted, fontSize:11, marginLeft:4,
              padding:"2px 6px", borderRadius:4,
            }}
          >✕ Clear</button>
        </div>
      )}
    </div>
  )
}

// ── LOGIN ─────────────────────────────────────────────────────────────────────
const LoginPage = ({onLogin}) => {
  const [hov,setHov] = useState(false)
  return (
    <div style={{display:"flex",height:"100vh",fontFamily:"system-ui,-apple-system,sans-serif"}}>
      <div style={{
        width:"41%",display:"flex",flexDirection:"column",
        alignItems:"center",justifyContent:"center",
        background:C.white,padding:"0 60px",
      }}>
        <Logo/>
        <div style={{height:36}}/>
        <h1 style={{
          fontSize:26,fontWeight:800,color:C.textPrimary,
          margin:0,textAlign:"center",letterSpacing:"-0.5px",
        }}>Welcome back</h1>
        <p style={{
          fontSize:13,color:C.textSecond,marginTop:10,
          textAlign:"center",lineHeight:1.65,maxWidth:260,
        }}>Sign in to access the employee time cost allocation platform</p>
        <div style={{height:32}}/>
        <button
          onClick={onLogin}
          onMouseEnter={()=>setHov(true)}
          onMouseLeave={()=>setHov(false)}
          style={{
            width:"100%",background:hov?C.redDark:C.red,
            color:"white",border:"none",borderRadius:9,
            padding:"13px 0",fontSize:14,fontWeight:600,
            cursor:"pointer",transition:"background 0.15s",
          }}
        >Sign in with Keycloak →</button>
        <p style={{fontSize:11,color:C.textMuted,marginTop:12,textAlign:"center"}}>
          Enterprise SSO · Secured by Keycloak
        </p>
      </div>

      <div style={{
        flex:1,position:"relative",overflow:"hidden",
        background:`linear-gradient(140deg,${C.red} 0%,${C.redDark} 100%)`,
        display:"flex",alignItems:"center",
      }}>
        {[{w:440,top:"-18%",right:"-10%",op:0.07},{w:300,bottom:"-14%",left:"-8%",op:0.06}].map((c,i)=>(
          <div key={i} style={{
            position:"absolute",width:c.w,height:c.w,
            borderRadius:"50%",background:"white",opacity:c.op,
            top:c.top,right:c.right,bottom:c.bottom,left:c.left,
          }}/>
        ))}
        <div style={{position:"relative",zIndex:1,padding:"0 68px"}}>
          <Logo variant="light"/>
          <h2 style={{
            color:"white",fontSize:28,fontWeight:800,
            marginTop:28,lineHeight:1.2,letterSpacing:"-0.5px",
          }}>Employee time.<br/>Precisely allocated.</h2>
          <p style={{color:"rgba(255,255,255,0.72)",fontSize:13,marginTop:14,lineHeight:1.75,maxWidth:360}}>
            Monthly cost allocation reporting across all your companies,
            organizational units, and project hierarchies.
          </p>
          <Divider mt={28} mb={24}/>
          <div style={{display:"flex",flexDirection:"column",gap:14}}>
            {["Period-specific cost allocation reports","Anomaly detection and flagging","Multi-company, multi-OU support"].map(f=>(
              <div key={f} style={{display:"flex",alignItems:"center",gap:11}}>
                <div style={{
                  width:20,height:20,borderRadius:"50%",
                  background:"rgba(255,255,255,0.18)",
                  display:"flex",alignItems:"center",justifyContent:"center",flexShrink:0,
                }}>
                  <CheckCircle size={12} color="white"/>
                </div>
                <span style={{color:"white",fontSize:13}}>{f}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

// ── PERIOD SELECTION ──────────────────────────────────────────────────────────
const PeriodSelectionPage = ({onSelect}) => {
  const [mode,       setMode]       = useState("month")
  const [year,       setYear]       = useState(2024)
  const [month,      setMonth]      = useState(null)
  const [rangeStart, setRangeStart] = useState(null)
  const [rangeEnd,   setRangeEnd]   = useState(null)

  const recent = [
    {y:2024,m:8,l:"Aug 2024"},{y:2024,m:7,l:"Jul 2024"},
    {y:2024,m:6,l:"Jun 2024"},{y:2024,m:5,l:"May 2024"},
    {y:2024,m:4,l:"Apr 2024"},{y:2024,m:3,l:"Mar 2024"},
  ]

  const monthReady = mode==="month" && !!month
  const rangeReady = mode==="range" && !!rangeStart && !!rangeEnd

  const handleContinue = () => {
    if (monthReady) onSelect(year, month, null, null)
    if (rangeReady) onSelect(rangeStart.y, rangeStart.m, rangeStart, rangeEnd)
  }

  const btnLabel = () => {
    if (mode==="month") return month
      ? `View Dashboard — ${MONTHS_FULL[month-1]} ${year} →`
      : "Select a month to continue"
    if (!rangeStart) return "Click a day to set start date"
    if (!rangeEnd)   return "Now click an end date"
    return `View Dashboard — ${dateLabel(rangeStart)} → ${dateLabel(rangeEnd)}`
  }

  const ModeBtn = ({id, label}) => (
    <button
      onClick={() => { setMode(id); setMonth(null); setRangeStart(null); setRangeEnd(null) }}
      style={{
        flex:1, padding:"8px 0", border:"none", borderRadius:8, cursor:"pointer",
        fontSize:13, fontWeight:600,
        background: mode===id ? C.white : "transparent",
        color: mode===id ? C.textPrimary : C.textMuted,
        boxShadow: mode===id ? "0 1px 4px rgba(0,0,0,0.08)" : "none",
        transition:"all 0.15s",
      }}
    >{label}</button>
  )

  return (
    <div style={{
      minHeight:"100vh", background:C.pageBg,
      display:"flex", flexDirection:"column", alignItems:"center",
      justifyContent:"center", padding:"32px 16px",
      fontFamily:"system-ui,-apple-system,sans-serif",
    }}>
      <div style={{marginBottom:32}}><Logo/></div>

      <div style={{
        background:C.white, border:`1px solid ${C.border}`,
        borderRadius:16, padding:28, width:"100%", maxWidth:520,
        boxShadow:"0 2px 16px rgba(0,0,0,0.06)",
      }}>
        <h2 style={{fontSize:19,fontWeight:800,color:C.textPrimary,margin:0,letterSpacing:"-0.4px"}}>
          Select a reporting period
        </h2>
        <p style={{fontSize:13,color:C.textSecond,marginTop:6,marginBottom:20}}>
          Choose a full month or pick a custom date range.
        </p>

        <div style={{
          display:"flex", gap:4, padding:4,
          background:C.pageBg, borderRadius:12,
          marginBottom:22, border:`1px solid ${C.border}`,
        }}>
          <ModeBtn id="month" label="📅  Full Month"/>
          <ModeBtn id="range" label="📆  Custom Range"/>
        </div>

        {mode==="month" && (<>
          <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",marginBottom:14}}>
            <button onClick={()=>setYear(y=>y-1)} style={{background:"none",border:`1px solid ${C.border}`,borderRadius:8,cursor:"pointer",width:34,height:34,display:"flex",alignItems:"center",justifyContent:"center"}}>
              <ChevronLeft size={16} color={C.textSecond}/>
            </button>
            <span style={{fontSize:16,fontWeight:700,color:C.textPrimary}}>{year}</span>
            <button onClick={()=>setYear(y=>y+1)} style={{background:"none",border:`1px solid ${C.border}`,borderRadius:8,cursor:"pointer",width:34,height:34,display:"flex",alignItems:"center",justifyContent:"center"}}>
              <ChevronRight size={16} color={C.textSecond}/>
            </button>
          </div>

          <div style={{display:"grid",gridTemplateColumns:"repeat(4,1fr)",gap:8,marginBottom:16}}>
            {MONTHS_SHORT.map((m,i)=>{
              const mn=i+1, active=month===mn
              return (
                <button key={m} onClick={()=>setMonth(mn)} style={{
                  position:"relative",padding:"12px 0",borderRadius:10,
                  border:active?"none":`1px solid ${C.border}`,cursor:"pointer",
                  fontSize:13,fontWeight:active?700:400,
                  background:active?C.red:C.white,
                  color:active?"white":C.textPrimary,transition:"all 0.12s",
                }}
                onMouseEnter={e=>{if(!active)e.currentTarget.style.background=C.redLight}}
                onMouseLeave={e=>{if(!active)e.currentTarget.style.background=C.white}}>
                  {m}
                </button>
              )
            })}
          </div>
        </>)}

        {mode==="range" && (
          <DateRangePicker
            startDate={rangeStart} endDate={rangeEnd}
            onStartChange={setRangeStart} onEndChange={setRangeEnd}
          />
        )}

        <Divider mt={20} mb={18}/>

        <button
          onClick={handleContinue}
          disabled={!monthReady && !rangeReady}
          style={{
            width:"100%",padding:"13px 0",borderRadius:9,border:"none",
            fontSize:13,fontWeight:600,
            cursor:(monthReady||rangeReady)?"pointer":"not-allowed",
            background:(monthReady||rangeReady)?C.red:"#E0E0E0",
            color:(monthReady||rangeReady)?"white":"#9E9E9E",
            transition:"background 0.15s",
          }}
          onMouseEnter={e=>{if(monthReady||rangeReady)e.currentTarget.style.background=C.redDark}}
          onMouseLeave={e=>{if(monthReady||rangeReady)e.currentTarget.style.background=C.red}}
        >{btnLabel()}</button>
      </div>

      <div style={{
        width:"100%",maxWidth:520,marginTop:14,
        background:C.white,border:`1px solid ${C.border}`,
        borderRadius:16,padding:"20px 24px",
      }}>
        <div style={{fontSize:11,fontWeight:700,color:C.textMuted,letterSpacing:"0.08em",textTransform:"uppercase",marginBottom:14}}>
          Recent periods
        </div>
        {recent.map(({y,m,l},idx)=>{
          return (
            <div key={l} onClick={()=>onSelect(y,m,null,null)}
              style={{display:"flex",alignItems:"center",justifyContent:"space-between",padding:"11px 0",cursor:"pointer",borderBottom:idx<recent.length-1?`1px solid ${C.border}`:"none"}}
              onMouseEnter={e=>e.currentTarget.style.opacity="0.7"}
              onMouseLeave={e=>e.currentTarget.style.opacity="1"}
            >
              <div style={{display:"flex",alignItems:"center",gap:10}}>
                <Calendar size={14} color={C.textMuted}/>
                <span style={{fontSize:13,fontWeight:500,color:C.textPrimary}}>{l}</span>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

// ── SIDEBAR ───────────────────────────────────────────────────────────────────
const Sidebar = ({currentPage,setCurrentPage,onLogout,userRoles}) => {
  const hasRole = (role) => userRoles.includes(role)

  // 🚀 FIX: Hide combined "Dashboard" from non-admins
  const nav = [
    {id:"dashboard",label:"Dashboard",icon:BarChart2,
     show: hasRole('DATA_ADMIN')},          // Admin sees ONLY the combined view
    {id:"financial",label:"Financial Officer",icon:FileText,
     show: hasRole('FINANCIAL_OFFICER')},   // Only the Financial Officer sees this
    {id:"product",label:"Product Manager",icon:BarChart2,
     show: hasRole('PRODUCT_MANAGER')},     // Only the Product Manager sees this
  ].filter(n => n.show)

  return (
    <div style={{
      width:220,background:C.white,borderRight:`1px solid ${C.border}`,
      display:"flex",flexDirection:"column",flexShrink:0,
      height:"100vh",position:"sticky",top:0,
    }}>
      <div style={{padding:"18px 16px 20px",borderBottom:`1px solid ${C.border}`}}>
        <Logo/>
      </div>
      <nav style={{flex:1,padding:"10px 8px",display:"flex",flexDirection:"column",gap:2}}>
        {nav.map(({id,label,icon:Icon})=>{
          const active=currentPage===id
          return (
            <button
              key={id}
              onClick={()=>setCurrentPage(id)}
              style={{
                display:"flex",alignItems:"center",gap:9,
                padding:"10px 11px",borderRadius:8,border:"none",
                cursor:"pointer",textAlign:"left",width:"100%",
                background:active?C.redLight:"transparent",
                color:active?C.red:C.textSecond,
                fontWeight:active?600:400,fontSize:13,
                borderLeft:active?`3px solid ${C.red}`:"3px solid transparent",
                transition:"all 0.12s",
              }}
              onMouseEnter={e=>{if(!active)e.currentTarget.style.background=C.pageBg}}
              onMouseLeave={e=>{if(!active)e.currentTarget.style.background="transparent"}}
            >
              <Icon size={16} color={active?C.red:"#B0B0B0"} strokeWidth={2}/>
              {label}
            </button>
          )
        })}
      </nav>
      <div style={{padding:"10px 8px",borderTop:`1px solid ${C.border}`}}>
        <button
          onClick={onLogout}
          style={{
            display:"flex",alignItems:"center",gap:9,
            padding:"10px 11px",borderRadius:8,border:"none",
            cursor:"pointer",background:"transparent",width:"100%",
            color:C.textMuted,fontSize:13,transition:"all 0.12s",
          }}
          onMouseEnter={e=>e.currentTarget.style.background=C.pageBg}
          onMouseLeave={e=>e.currentTarget.style.background="transparent"}
        >
          <LogOut size={15} color="#C0C0C0"/>Sign out
        </button>
      </div>
    </div>
  )
}

// ── TOP BAR ───────────────────────────────────────────────────────────────────
const TopBar = ({period,onChangePeriod,currentPage}) => {
  const [open,setOpen] = useState(false)
  const ref = useRef(null)
  useEffect(()=>{
    const h=e=>{if(ref.current&&!ref.current.contains(e.target))setOpen(false)}
    document.addEventListener("mousedown",h)
    return()=>document.removeEventListener("mousedown",h)
  },[])

  const recent=[
    {y:2024,m:8},{y:2024,m:7},{y:2024,m:6},
    {y:2024,m:5},{y:2024,m:4},{y:2024,m:3},
  ]

  const pageTitle = currentPage === "financial" ? "Financial Officer Dashboard" :
                    currentPage === "product" ? "Product Manager Dashboard" :
                    "Dashboard"

  return (
    <div style={{
      height:58,background:C.white,borderBottom:`1px solid ${C.border}`,
      display:"flex",alignItems:"center",justifyContent:"space-between",
      padding:"0 24px",flexShrink:0,position:"relative",zIndex:10,
    }}>
      <span style={{fontSize:16,fontWeight:700,color:C.textPrimary}}>{pageTitle}</span>
      <div style={{display:"flex",alignItems:"center",gap:16}}>
        <div ref={ref} style={{position:"relative"}}>
          <button
            onClick={()=>setOpen(o=>!o)}
            style={{
              display:"flex",alignItems:"center",gap:7,
              background:C.redLight,border:`1.5px solid ${C.redMid}`,
              borderRadius:8,padding:"7px 12px",cursor:"pointer",
              fontSize:13,fontWeight:700,color:C.red,
            }}
          >
            <Calendar size={14} color={C.red}/>
            {period.isCustom
            ? `${MONTHS_SHORT[period.startDate.m-1]} ${period.startDate.d} – ${MONTHS_SHORT[period.endDate.m-1]} ${period.endDate.d}, ${period.year}`
            : `${MONTHS_SHORT[period.month-1]} ${period.year}`
          }
            <ChevronDown size={14} color={C.red}/>
          </button>
          {open&&(
            <div style={{
              position:"absolute",top:"calc(100% + 8px)",right:0,
              background:C.white,border:`1px solid ${C.border}`,
              borderRadius:12,padding:16,width:200,zIndex:100,
              boxShadow:"0 8px 24px rgba(0,0,0,0.10)",
            }}>
              <div style={{
                fontSize:11,fontWeight:700,color:C.textMuted,
                letterSpacing:"0.08em",textTransform:"uppercase",marginBottom:10,
              }}>Change period</div>
              {recent.map(({y,m})=>{
                const active=period.year===y&&period.month===m
                return (
                  <button
                    key={pk(y,m)}
                    onClick={()=>{onChangePeriod(y,m);setOpen(false)}}
                    style={{
                      display:"block",width:"100%",textAlign:"left",
                      padding:"8px 10px",border:"none",borderRadius:6,
                      cursor:"pointer",fontSize:13,
                      background:active?C.redLight:"transparent",
                      color:active?C.red:C.textPrimary,
                      fontWeight:active?600:400,
                      transition:"background 0.1s",
                    }}
                    onMouseEnter={e=>{if(!active)e.currentTarget.style.background=C.pageBg}}
                    onMouseLeave={e=>{if(!active)e.currentTarget.style.background="transparent"}}
                  >
                    {MONTHS_FULL[m-1]} {y}
                  </button>
                )
              })}
            </div>
          )}
        </div>
        <Bell size={17} color="#C0C0C0"/>
        <div style={{display:"flex",alignItems:"center",gap:8}}>
          <div style={{
            width:30,height:30,borderRadius:"50%",background:C.red,
            display:"flex",alignItems:"center",justifyContent:"center",
            fontSize:11,fontWeight:800,color:"white",
          }}></div>
          <span style={{fontSize:13,color:C.textSecond}}></span>
        </div>
      </div>
    </div>
  )
}

// ── DASHBOARD (Combined view for Admins) ─────────────────────────────────────
const DashboardPage = ({ period }) => {
  const [prod, setProd] = useState(null)
  const [fin, setFin] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const pl = period.isCustom
    ? `${dateLabel(period.startDate)} → ${dateLabel(period.endDate)}`
    : `${MONTHS_FULL[period.month-1]} ${period.year}`

  useEffect(() => {
    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        await keycloak.updateToken(60)
        const opts = { headers: { Authorization: `Bearer ${keycloak.token}` } }
        
        // 🚀 FIX: Use allSettled so one 403 doesn't crash the whole page
        const [p, f] = await Promise.allSettled([
          fetch(`/api/dashboard/product?year=${period.year}&month=${period.month}`, opts)
            .then(r => (r.ok ? r.json() : null)),
          fetch(`/api/dashboard/financial?year=${period.year}&month=${period.month}`, opts)
            .then(r => (r.ok ? r.json() : null)),
        ])
        
        const prodData = p.status === "fulfilled" ? p.value : null
        const finData  = f.status === "fulfilled" ? f.value : null

        if (!prodData && !finData) throw new Error("No dashboard data available for your role")
        
        setProd(prodData)
        setFin(finData)
      } catch (e) {
        setError(e.message)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [period.year, period.month])

  if (loading) return <div style={{display:"flex",alignItems:"center",justifyContent:"center",height:"100%",color:C.textMuted}}>Loading...</div>
  if (error) return <div style={{padding:24}}><div style={{background:C.amberLight,border:`1px solid ${C.amber}`,borderRadius:12,padding:16,color:C.amber,fontSize:13}}>⚠️ {error}</div></div>

  const ps = prod?.statCards || {}
  const fs = fin?.statCards || {}
  
  const members = ps.allocatedHeadcount ?? 0
  const manDays = ps.totalManDays ?? 0
  const laborCost = fs.totalLaborCost ?? 0
  const anomalies = (ps.openAnomalies ?? 0) + (fs.anomalies ?? 0)
  const utilization = ps.utilization ?? 0
  const billableRatio = fs.billableRatio ?? 0

  const companies = (prod?.headcountByCompany || []).map(r => ({ company: r.companyName, members: r.headcount }))
   const ouBreakdown = (fin?.costByOu || []).map(r => ({
    ou: r.ouName,
    pct: Math.round(r.percentage ?? 0)
  }))

  const nature = prod?.activityNatureBreakdown || []
  const natureTotal = nature.reduce((s, n) => s + (n.manDays || 0), 0) || 1
  const activityNature = nature.map(n => ({ name: n.natureName, value: Math.round(((n.manDays || 0) / natureTotal) * 100), color: n.color }))
  const trendData = (prod?.manDaysTrend || []).map(t => ({ period: String(t.period).slice(0, 3), lines: t.manDays }))

  return (
    <div style={{background:C.pageBg,minHeight:"100%",padding:24}}>
      <div style={{display:"grid",gridTemplateColumns:"repeat(4,1fr)",gap:14}}>
        <StatCard icon={Users} value={fmt(members)} label="Active Members" sub={`on products · ${pl}`}/>
        <StatCard icon={TrendingUp} value={fmt(manDays)} label="Total Man-Days" sub="booked this period"/>
        <StatCard icon={FileText} value={fmtCurrency(laborCost)} label="Total Labor Cost" sub="man-days × day rate"/>
        <StatCard icon={AlertTriangle} value={fmt(anomalies)} label="Anomalies" sub={anomalies > 0 ? "need review" : "none detected"} warning={anomalies > 0}/>
      </div>

      <div style={{display:"grid",gridTemplateColumns:"1fr 300px",gap:14,marginTop:14}}>
        <div style={{background:C.white,border:`1px solid ${C.border}`,borderRadius:12,padding:20}}>
          <div style={{display:"flex",alignItems:"baseline",justifyContent:"space-between",marginBottom:18}}>
            <div>
              <div style={{fontSize:14,fontWeight:700,color:C.textPrimary}}>Members by Company</div>
              <div style={{fontSize:12,color:C.textMuted,marginTop:2}}>Real headcount · {pl}</div>
            </div>
            <div style={{fontSize:12,color:C.textMuted}}>Total: {fmt(members)}</div>
          </div>
          <ResponsiveContainer width="100%" height={210}>
            <BarChart data={companies} barCategoryGap="36%">
              <CartesianGrid vertical={false} stroke={C.border}/>
              <XAxis dataKey="company" tick={{fontSize:11,fill:C.textMuted}} axisLine={false} tickLine={false}/>
              <YAxis tick={{fontSize:11,fill:C.textMuted}} axisLine={false} tickLine={false}/>
              <Tooltip content={<ChartTip/>} cursor={{fill:C.redLight}}/>
              <Bar dataKey="members" fill={C.red} radius={[4,4,0,0]}/>
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div style={{background:C.greenLight,border:"1.5px solid #BBF7D0",borderRadius:12,padding:22,display:"flex",flexDirection:"column"}}>
          <div style={{display:"flex",alignItems:"center",gap:8,marginBottom:12}}>
            <CheckCircle size={18} color={C.green}/>
            <span style={{fontSize:14,fontWeight:700,color:C.green}}>Live PostgreSQL data</span>
          </div>
          <div style={{fontSize:12,color:"#4B7A5E",lineHeight:1.8}}>
            Billable ratio: <b>{Math.round(billableRatio)}%</b><br/>
            Utilization: <b>{Math.round(utilization)}%</b><br/>
            Budget variance: <b>{fmt(fs.budgetVariance ?? 0)}</b><br/>
            Anomaly cost impact: <b>{fmtCurrency(fs.anomalyCostImpact ?? 0)}</b>
          </div>
        </div>
      </div>

      <div style={{display:"grid",gridTemplateColumns:"1fr 1fr 1fr",gap:14,marginTop:14}}>
        <div style={{background:C.white,border:`1px solid ${C.border}`,borderRadius:12,padding:20}}>
          <div style={{fontSize:14,fontWeight:700,color:C.textPrimary,marginBottom:4}}>Activity Nature</div>
          <div style={{fontSize:12,color:C.textMuted,marginBottom:16}}>Real distribution</div>
          <div style={{position:"relative",height:160}}>
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={activityNature} cx="50%" cy="50%" innerRadius={50} outerRadius={70} dataKey="value" paddingAngle={2} startAngle={90} endAngle={-270}>
                  {activityNature.map((e, i) => (<Cell key={i} fill={e.color} strokeWidth={0}/>))}
                </Pie>
                <Tooltip formatter={(v) => [`${v}%`]} contentStyle={{fontSize:12,borderRadius:8,border:`1px solid ${C.border}`}}/>
              </PieChart>
            </ResponsiveContainer>
            <div style={{position:"absolute",top:"50%",left:"50%",transform:"translate(-50%,-50%)",textAlign:"center",pointerEvents:"none"}}>
              <div style={{fontSize:18,fontWeight:800,color:C.textPrimary,lineHeight:1}}>{activityNature.length}</div>
              <div style={{fontSize:10,color:C.textMuted,marginTop:2}}>types</div>
            </div>
          </div>
          <div style={{display:"flex",flexDirection:"column",gap:7,marginTop:14}}>
            {activityNature.map(({name, value, color}) => (
              <div key={name} style={{display:"flex",alignItems:"center",gap:8}}>
                <div style={{width:8,height:8,borderRadius:2,background:color,flexShrink:0}}/>
                <span style={{fontSize:11,color:C.textSecond,flex:1}}>{name}</span>
                <span style={{fontSize:11,fontWeight:700,color:C.textPrimary}}>{value}%</span>
              </div>
            ))}
          </div>
        </div>

        <div style={{background:C.white,border:`1px solid ${C.border}`,borderRadius:12,padding:20}}>
          <div style={{fontSize:14,fontWeight:700,color:C.textPrimary,marginBottom:4}}>Man-Days Trend</div>
          <div style={{fontSize:12,color:C.textMuted,marginBottom:16}}>Last 6 periods (real)</div>
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={trendData} margin={{left:-10,right:8}}>
              <CartesianGrid vertical={false} stroke={C.border}/>
              <XAxis dataKey="period" tick={{fontSize:11,fill:C.textMuted}} axisLine={false} tickLine={false}/>
              <YAxis tick={{fontSize:11,fill:C.textMuted}} axisLine={false} tickLine={false} domain={["auto","auto"]}/>
              <Tooltip content={<TrendTip/>}/>
              <Line type="monotone" dataKey="lines" stroke={C.red} strokeWidth={2.5} dot={{fill:C.red,r:4,strokeWidth:0}} activeDot={{r:5,fill:C.red,strokeWidth:0}}/>
            </LineChart>
          </ResponsiveContainer>
        </div>

        <div style={{background:C.white,border:`1px solid ${C.border}`,borderRadius:12,padding:20}}>
          <div style={{fontSize:13,fontWeight:700,color:C.textPrimary,marginBottom:12}}>Cost by OU</div>
          <div style={{display:"flex",flexDirection:"column",gap:9}}>
            {ouBreakdown.map(({ou, pct}) => (
              <div key={ou}>
                <div style={{display:"flex",justifyContent:"space-between",marginBottom:4}}>
                  <span style={{fontSize:11,color:C.textPrimary}}>{ou}</span>
                  <span style={{fontSize:11,fontWeight:600,color:C.textSecond}}>{pct}%</span>
                </div>
                <div style={{height:4,background:C.border,borderRadius:2}}>
                  <div style={{height:"100%",background:C.red,borderRadius:2,width:`${pct}%`,transition:"width 0.5s ease"}}/>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

// ── APP ───────────────────────────────────────────────────────────────────────
export default function App() {
  const [page,      setPage]      = useState("login")
  const [period,    setPeriod]    = useState(null)
  const [navPage,   setNavPage]   = useState("dashboard")
  const [isAuthChecked, setIsAuthChecked] = useState(false)
  const [userRoles, setUserRoles] = useState([])

  useEffect(() => {
    keycloak.init({ onLoad: 'check-sso' })
      .then((authenticated) => {
        if (authenticated) {
          const token = keycloak.token
          let roles = []
          try {
            const decoded = JSON.parse(atob(token.split('.')[1]))
            roles = decoded['realm_access']?.roles || []
            setUserRoles(roles)
          } catch (e) {
            console.error("Failed to decode token:", e)
          }
          // 🚀 SMART ROUTING: Send user to their home dashboard
          if (roles.includes('DATA_ADMIN')) setNavPage('dashboard')
          else if (roles.includes('FINANCIAL_OFFICER')) setNavPage('financial')
          else if (roles.includes('PRODUCT_MANAGER')) setNavPage('product')
          
          setPage("period");
        }
        setIsAuthChecked(true);
      })
      .catch((err) => {
        console.error("Keycloak init failed", err);
        setIsAuthChecked(true);
      });
  }, []);

  const handleRealLogin = () => keycloak.login();
  const handleRealLogout = () => keycloak.logout();

  const hasRole = (role) => userRoles.includes(role)

  // 🚀 SIMPLE ROUTER: Show the right component based on nav and role
  const getDashboardForRole = () => {
    if (navPage === "financial" && (hasRole('DATA_ADMIN') || hasRole('FINANCIAL_OFFICER')))
      return <FinancialDashboard period={period} onPeriodChange={setPeriod} />
    
    if (navPage === "product" && (hasRole('DATA_ADMIN') || hasRole('PRODUCT_MANAGER')))
      return <ProductDashboard period={period} onPeriodChange={setPeriod} />
    
    if (hasRole('DATA_ADMIN'))
      return <DashboardPage period={period} />
    
    // Fallback for single-role users
    if (hasRole('FINANCIAL_OFFICER')) return <FinancialDashboard period={period} onPeriodChange={setPeriod} />
    if (hasRole('PRODUCT_MANAGER')) return <ProductDashboard period={period} onPeriodChange={setPeriod} />
    
    return <DashboardPage period={period} />
  }

  if (!isAuthChecked) {
    return (
      <div style={{display:"flex",height:"100vh",alignItems:"center",justifyContent:"center",fontFamily:"system-ui",color:C.textMuted}}>
        Checking authentication...
      </div>
    );
  }

  if(page==="login") return <LoginPage onLogin={handleRealLogin} />

  if(page==="period") return (
    <PeriodSelectionPage
      onSelect={(y,m,sDate,eDate)=>{
        setPeriod({year:y,month:m,startDate:sDate,endDate:eDate,isCustom:!!sDate})
        setPage("dashboard")
      }}
    />
  )

  return (
    <div style={{display:"flex",height:"100vh",overflow:"hidden",fontFamily:"system-ui,-apple-system,sans-serif"}}>
      <Sidebar
        currentPage={navPage}
        setCurrentPage={setNavPage}
        onLogout={handleRealLogout}
        userRoles={userRoles}
      />
      <div style={{flex:1,display:"flex",flexDirection:"column",overflow:"hidden"}}>
        <TopBar period={period} onChangePeriod={(y,m)=>setPeriod({year:y,month:m})} currentPage={navPage} />
        <div style={{flex:1,overflowY:"auto"}}>
          {getDashboardForRole()}
        </div>
      </div>
    </div>
  )
}