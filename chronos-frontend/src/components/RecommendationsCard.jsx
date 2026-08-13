import { useEffect, useState } from "react"
import keycloak from "../keycloak"
import { Lightbulb, AlertTriangle, CheckCircle, Info } from "lucide-react"

const SEV = {
  OK:      { color: "#16A34A", bg: "#F0FDF4", Icon: CheckCircle },
  INFO:    { color: "#2563EB", bg: "#EFF6FF", Icon: Info },
  WARNING: { color: "#D97706", bg: "#FFFBEB", Icon: AlertTriangle },
}

const RecommendationsCard = ({ period }) => {
  const [insights, setInsights] = useState([])

  useEffect(() => {
    const load = async () => {
      try {
        const res = await fetch(`/api/insights/${period.year}/${period.month}`, {
          headers: { Authorization: `Bearer ${keycloak.token}` }
        })
        if (res.ok) setInsights(await res.json())
      } catch (e) { console.error(e) }
    }
    load()
  }, [period.year, period.month])

  if (!insights.length) return null

  return (
    <div style={{background:"#fff",border:"1px solid #E8E8E8",borderRadius:12,padding:20,marginTop:14}}>
      <div style={{display:"flex",alignItems:"center",gap:8,marginBottom:14}}>
        <Lightbulb size={16} color="#C62828"/>
        <span style={{fontSize:14,fontWeight:700}}>Recommendations</span>
        <span style={{fontSize:11,color:"#A0A0A0"}}>auto-generated from live data</span>
      </div>
      <div style={{display:"flex",flexDirection:"column",gap:8}}>
        {insights.map((ins, i) => {
          const s = SEV[ins.severity] || SEV.INFO
          const Icon = s.Icon
          return (
            <div key={i} style={{display:"flex",gap:10,alignItems:"flex-start",background:s.bg,border:`1px solid ${s.color}33`,borderRadius:8,padding:"10px 12px"}}>
              <Icon size={15} color={s.color} style={{marginTop:1,flexShrink:0}}/>
              <div>
                <div style={{fontSize:12,fontWeight:700,color:s.color}}>{ins.title}</div>
                <div style={{fontSize:12,color:"#4B5563",marginTop:2}}>{ins.message}</div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default RecommendationsCard