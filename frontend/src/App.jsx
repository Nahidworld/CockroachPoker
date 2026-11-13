import { useEffect, useState, useCallback } from 'react'
import {
  loadRoom, saveRoom, randomCode, createRoomState, joinRoomState,
} from './game'
import Home from './pages/Home'
import Lobby from './pages/Lobby'
import Table from './pages/Table'
import Winner from './pages/Winner'

// my player id lives in this tab only (sessionStorage)
const myIdKey = (code) => `cp_pid_${code}`

export default function App() {
  const [code, setCode] = useState(() => sessionStorage.getItem('cp_code'))
  const [state, setState] = useState(() => (code ? loadRoom(code) : null))

  useEffect(() => {
    if (code) sessionStorage.setItem('cp_code', code)
    else sessionStorage.removeItem('cp_code')
  }, [code])

  // keep this tab in sync when other tabs change the room
  useEffect(() => {
    if (!code) return
    const onStorage = (e) => {
      if (e.key === `cp_room_${code}`) setState(loadRoom(code))
    }
    window.addEventListener('storage', onStorage)
    setState(loadRoom(code))
    return () => window.removeEventListener('storage', onStorage)
  }, [code])

  const update = useCallback((updater) => {
    if (!code) return
    const current = loadRoom(code)
    if (!current) return
    const next = updater(current)
    saveRoom(code, next)
    setState(next)
  }, [code])

  const create = (name) => {
    const c = randomCode() // fresh random code every time
    const s = createRoomState(c, name)
    saveRoom(c, s)
    sessionStorage.setItem(myIdKey(c), 'p1')
    setCode(c)
    setState(s)
  }

  const join = (name, joinCode) => {
    const s = loadRoom(joinCode)
    if (!s) return 'Room not found — check the 4-digit code.'
    if (s.phase !== 'lobby') return 'That game already started.'
    if (s.players.length >= 6) return 'Room is full (6 players max).'
    if (s.players.some((p) => p.name.toLowerCase() === name.trim().toLowerCase()))
      return 'That name is already taken in this room.'
    const next = joinRoomState(s, name.trim())
    saveRoom(joinCode, next)
    sessionStorage.setItem(myIdKey(joinCode), next.players[next.players.length - 1].id)
    setCode(joinCode)
    setState(next)
    return null
  }

  const exit = () => {
    setCode(null)
    setState(null)
  }

  if (!state) return <Home onCreate={create} onJoin={join} />

  const myId = sessionStorage.getItem(myIdKey(code))
  const me = state.players.find((p) => p.id === myId)

  if (state.phase === 'lobby')
    return <Lobby state={state} me={me} update={update} exit={exit} />
  if (state.phase === 'gameover')
    return <Winner state={state} me={me} update={update} exit={exit} />
  return <Table state={state} myId={myId} me={me} update={update} exit={exit} />
}
