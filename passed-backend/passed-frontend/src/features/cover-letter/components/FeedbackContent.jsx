import styles from './FeedbackContent.module.css'

export function normalizeFeedbackText(value) {
  return String(value ?? '')
    .replace(/\r\n?/g, '\n')
    .replace(/\\r\\n|\\n|\\r/g, '\n')
    .replace(/\\t/g, ' ')
    .replace(/\\"/g, '"')
    .replace(/(^|\n)\s*```(?:markdown|md|text)?\s*(?=\n|$)/gi, '$1')
    .replace(/(^|\n)\s*```\s*(?=\n|$)/g, '$1')
    .trim()
}

function cleanInlineText(text) {
  return text
    .replace(/\[([^\]]+)]\([^)]+\)/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\\([\\`*_[\]{}()#+.!>~-])/g, '$1')
}

function inlineParts(text) {
  const cleaned = cleanInlineText(text)
  return cleaned
    .split(/(\*\*[^*]+\*\*|__[^_]+__|\*[^*]+\*|_[^_]+_|~~[^~]+~~)/g)
    .filter(Boolean)
    .map((part, index) => {
      const key = `${part}-${index}`
      if ((part.startsWith('**') && part.endsWith('**'))
        || (part.startsWith('__') && part.endsWith('__'))) {
        return <strong key={key}>{part.slice(2, -2)}</strong>
      }
      if ((part.startsWith('*') && part.endsWith('*'))
        || (part.startsWith('_') && part.endsWith('_'))) {
        return <em key={key}>{part.slice(1, -1)}</em>
      }
      if (part.startsWith('~~') && part.endsWith('~~')) {
        return <span key={key}>{part.slice(2, -2)}</span>
      }
      return <span key={key}>{part.replace(/\*\*|__|~~/g, '')}</span>
    })
}

const FeedbackContent = ({ text }) => {
  const lines = normalizeFeedbackText(text).split('\n').map((line) => line.trim())
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
    const bullet = line.match(/^[-*+•]\s+(.+)$/)
    const numbered = line.match(/^\d+[.)]\s+(.+)$/)
    if (heading) {
      flushList()
      blocks.push(<h5 key={`heading-${blocks.length}`}>{inlineParts(heading[1])}</h5>)
    } else if (bullet || numbered) {
      const nextOrdered = Boolean(numbered)
      if (list.length && ordered !== nextOrdered) flushList()
      ordered = nextOrdered
      list.push((bullet || numbered)[1])
    } else if (!/^[-*_]{3,}$/.test(line)) {
      flushList()
      blocks.push(<p key={`paragraph-${blocks.length}`}>{inlineParts(line.replace(/^>\s*/, ''))}</p>)
    }
  })
  flushList()
  return <div className={styles.content}>{blocks}</div>
}

export default FeedbackContent
