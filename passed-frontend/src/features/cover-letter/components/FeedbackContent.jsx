import styles from './FeedbackContent.module.css'

function inlineParts(text) {
  return text.split(/(\*\*[^*]+\*\*|__[^_]+__)/g).filter(Boolean).map((part, index) => {
    const isStrong = (part.startsWith('**') && part.endsWith('**'))
      || (part.startsWith('__') && part.endsWith('__'))
    return isStrong
      ? <strong key={`${part}-${index}`}>{part.slice(2, -2)}</strong>
      : <span key={`${part}-${index}`}>{part}</span>
  })
}

const FeedbackContent = ({ text }) => {
  const lines = String(text ?? '').split(/\r?\n/)
    .map((line) => line.replace(/^```(?:markdown|text)?\s*$/i, '').replace(/```\s*$/, '').trim())
  const blocks = []
  let list = []
  let ordered = false

  const flushList = () => {
    if (!list.length) return
    const Tag = ordered ? 'ol' : 'ul'
    blocks.push(<Tag key={`list-${blocks.length}`}>{list.map((item, index) => (
      <li key={`${item}-${index}`}>{inlineParts(item)}</li>
    ))}</Tag>)
    list = []
  }

  lines.forEach((line) => {
    if (!line) {
      flushList()
      return
    }
    const heading = line.match(/^#{1,6}\s+(.+)$/) || line.match(/^\[([^\]]+)]$/)
    const bullet = line.match(/^[-*+]\s+(.+)$/)
    const numbered = line.match(/^\d+[.)]\s+(.+)$/)
    if (heading) {
      flushList()
      blocks.push(<h5 key={`heading-${blocks.length}`}>{inlineParts(heading[1])}</h5>)
    } else if (bullet || numbered) {
      const nextOrdered = Boolean(numbered)
      if (list.length && ordered !== nextOrdered) flushList()
      ordered = nextOrdered
      list.push((bullet || numbered)[1])
    } else {
      flushList()
      blocks.push(<p key={`paragraph-${blocks.length}`}>{inlineParts(line.replace(/^>\s*/, ''))}</p>)
    }
  })
  flushList()
  return <div className={styles.content}>{blocks}</div>
}

export default FeedbackContent
