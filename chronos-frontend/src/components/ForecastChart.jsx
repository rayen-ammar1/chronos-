import { useEffect, useState } from "react"
import keycloak from "../keycloak"
import {
  ComposedChart, Line, Area, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, ReferenceLine
} from "recharts"
import { TrendingUp } from "lucide-react"

const fmtEur = v => "€" + Number(v ?? 0).toLocaleString(undefined, { maximumFractionDigits: 0 })

const Tip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null
  const rows = payload.filter(p => p.dataKey === "actual" || p.dataKey === "forecast")
  if (!rows.length) return null
  return (
    <div style={{background:"#fff",border:"1px solid #E8E8E8",borderRadius:8,padding:"8px 12px",fontSize:12,boxShadow:"0 4px 12px rgba(0,0,0,.08)"}}>
      <div style={{color:"#A0A0A0",marginBottom:4}}>{label}</div>
      {rows.map(r => (
        <div key={r.dataKey} style={{display:"flex",justifyContent:"space-between",gap:16}}>
          <span style={{color:"#6B6B6B"}}>{r.dataKey === "actual" ? "Actual" : "Forecast"}</span>
          <span style={{fontWeight:700,color:"#1A1A1A"}}>{fmtEur(r.value)}</span>
        </div>
      ))}
    </div>
  )
}

const ForecastChart = ({ period }) => {
  const [data, setData] = useState([])
  const [divider, setDivider] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let alive = true
    const load = async () => {
      setLoading(true); setError(null)
      try {
        const res = await fetch(`/api/forecast/cost/${period.year}/${period.month}`, {
          headers: { Authorization: `Bearer ${keycloak.token}` }
        })
        if (!res.ok) throw new Error(res.status)
        const json = await res.json()

        const merged = [
          ...json.history.map(h => ({ period: h.period, actual: h.value, forecast: null, band: null })),
          ...json.forecast.map((f, i) => ({
            period: f.period,
            actual: i === 0 ? f.value : null,
            forecast: f.value,
            band: [f.lowerBound, f.upperBound],
          })),
        ]
        if (alive) {
          setData(merged)
          setDivider(json.history[json.history.length - 1]?.period ?? null)
        }
      } catch (e) { if (alive) setError("Forecast unavailable") }
      finally { if (alive) setLoading(false) }
    }
    load()
    return () => { alive = false }
  }, [period.year, period.month])

  return (
    <div style={{background:"#fff",border:"1px solid #E8E8E8",borderRadius:12,padding:20,marginTop:14}}>
      {/* Header + inline legend */}
      <div style={{display:"flex",alignItems:"flex-start",justifyContent:"space-between",marginBottom:10}}>
        <div>
          <div style={{display:"flex",alignItems:"center",gap:8}}>
            <TrendingUp size={16} color="#C62828"/>
            <span style={{fontSize:14,fontWeight:700,color:"#1A1A1A"}}>Predictive Cost Forecast</span>
          </div>
          <div style={{fontSize:11,color:"#A0A0A0",marginTop:2}}>
            OLS linear regression · last 6 periods analyzed · next 3 projected
          </div>
        </div>
        <div style={{display:"flex",gap:16,alignItems:"center",fontSize:11,color:"#6B6B6B"}}>
          <span style={{display:"flex",alignItems:"center",gap:6}}>
            <span style={{width:18,borderTop:"2.5px solid #C62828"}}/>Actual
          </span>
          <span style={{display:"flex",alignItems:"center",gap:6}}>
            <span style={{width:18,borderTop:"2.5px dashed #C62828"}}/>Forecast
          </span>
          <span style={{display:"flex",alignItems:"center",gap:6}}>
            <span style={{width:14,height:10,background:"rgba(198,40,40,.12)",borderRadius:2}}/>95% confidence
          </span>
        </div>
      </div>

      {loading ? (
        <div style={{height:260,display:"flex",alignItems:"center",justifyContent:"center",color:"#A0A0A0",fontSize:12}}>
          Running regression model…
        </div>
      ) : error || data.length === 0 ? (
        <div style={{height:260,display:"flex",alignItems:"center",justifyContent:"center",color:"#A0A0A0",fontSize:12}}>
          Not enough historical data to forecast this period.
        </div>
      ) : (
        <div style={{height:260}}>
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={data} margin={{top:10,right:12,left:0,bottom:0}}>
              <CartesianGrid vertical={false} stroke="#F0F0F0"/>
              <XAxis dataKey="period" tick={{fontSize:11,fill:"#A0A0A0"}} axisLine={false} tickLine={false}/>
              <YAxis tick={{fontSize:11,fill:"#A0A0A0"}} axisLine={false} tickLine={false}
                     tickFormatter={v => "€" + (v/1000).toFixed(0) + "k"} width={52}/>
              <Tooltip content={<Tip/>}/>
              {divider && <ReferenceLine x={divider} stroke="#D7D7D7" strokeDasharray="4 4"/>}
              <Area dataKey="band" stroke="none" fill="#C62828" fillOpacity={0.08} activeDot={false}/>
              <Line dataKey="actual" stroke="#C62828" strokeWidth={2.5}
                    dot={{r:3.5,strokeWidth:0,fill:"#C62828"}} connectNulls={false}/>
              <Line dataKey="forecast" stroke="#C62828" strokeWidth={2.5} strokeDasharray="6 4"
                    dot={{r:3.5,strokeWidth:0,fill:"#C62828"}} connectNulls={false}/>
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}

export default ForecastChart