import styles from './DynamicResumeSection.module.css'

const normalizeDate = (value) => {
  const digits = String(value ?? '').replace(/\D/g, '').slice(0, 8)
  if (digits.length !== 8) return value
  const year = Number(digits.slice(0, 4))
  const month = Number(digits.slice(4, 6))
  const day = Number(digits.slice(6, 8))
  const date = new Date(Date.UTC(year, month - 1, day))
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return value
  return `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)}`
}

const displayDate = (value) => value ? String(value).replaceAll('-', '.') : ''

const EditIcon = () => <svg viewBox="0 0 24 24" fill="none"><path d="m4 20 4.2-1 10.6-10.6-3.2-3.2L5 15.8 4 20Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" /><path d="m14.8 6 3.2 3.2" stroke="currentColor" strokeWidth="1.8" /></svg>
const DeleteIcon = () => <svg viewBox="0 0 24 24" fill="none"><path d="M5 7h14M9 7V4h6v3m2 0-1 13H8L7 7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>

const fieldControl = (field, value, onChange) => {
  if (field.type === 'select') {
    return (
      <select value={value ?? ''} onChange={(event) => onChange(event.target.value)} required={field.required}>
        <option value="">선택</option>
        {field.options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
    )
  }
  if (field.type === 'textarea') {
    return <textarea rows="4" value={value ?? ''} placeholder={field.placeholder} onChange={(event) => onChange(event.target.value)} />
  }
  if (field.type === 'date') {
    return <input type="text" inputMode="numeric" maxLength="10" value={value ?? ''} placeholder="YYYY-MM-DD" onChange={(event) => onChange(event.target.value)} onBlur={(event) => onChange(normalizeDate(event.target.value))} />
  }
  return <input type={field.type || 'text'} value={value ?? ''} placeholder={field.placeholder} required={field.required} onChange={(event) => onChange(event.target.value)} />
}

const Summary = ({ fields, item, onEdit, onRemove, title }) => {
  const titleField = fields.find((field) => field.summaryTitle) ?? fields[0]
  const summaryTitle = item[titleField.name] || title
  const dateFields = fields.filter((field) => field.type === 'date' && item[field.name])
  const period = dateFields.map((field) => displayDate(item[field.name])).join(' ~ ')
  const shortValues = fields
    .filter((field) => field.name !== titleField.name && field.type !== 'textarea' && field.type !== 'date' && item[field.name])
    .map((field) => field.options?.find((option) => option.value === item[field.name])?.label ?? item[field.name])
  const details = fields.filter((field) => field.type === 'textarea' && item[field.name])

  return (
    <article className={styles.summaryItem}>
      <div className={styles.summaryHeading}>
        <p><strong>{summaryTitle}</strong>{period && <span>{period}</span>}{shortValues.length > 0 && <span>{shortValues.join(' · ')}</span>}</p>
        <div className={styles.summaryActions}>
          <button type="button" aria-label={`${summaryTitle} 수정`} onClick={onEdit}><EditIcon /></button>
          <button type="button" aria-label={`${summaryTitle} 삭제`} onClick={onRemove}><DeleteIcon /></button>
        </div>
      </div>
      {details.map((field) => <p className={styles.detail} key={field.name}>{item[field.name]}</p>)}
    </article>
  )
}

const DynamicResumeSection = ({ fields, items, editingIds, onAdd, onChange, onDone, onEdit, onRemove, sectionRef, title }) => (
  <section className={styles.section} ref={sectionRef}>
    <header>
      <div><h2>{title} <b>· 선택 입력</b></h2></div>
      <button type="button" onClick={onAdd}>항목 추가</button>
    </header>
    <div className={styles.items}>
      {items.map((item, index) => editingIds.has(item.clientId) ? (
        <article className={styles.editor} key={item.clientId}>
          <div className={styles.editorActions}><button type="button" onClick={() => onDone(item.clientId)}>완료</button><button className={styles.deleteText} type="button" onClick={() => onRemove(index)}>삭제</button></div>
          <div className={styles.grid}>
            {fields.map((field) => (
              <label className={field.wide ? styles.wide : ''} key={field.name}>
                <span>{field.label}</span>
                {fieldControl(field, item[field.name], (value) => onChange(index, field.name, value))}
              </label>
            ))}
          </div>
        </article>
      ) : (
        <Summary key={item.clientId} fields={fields} item={item} title={title} onEdit={() => onEdit(item.clientId)} onRemove={() => onRemove(index)} />
      ))}
    </div>
  </section>
)

export default DynamicResumeSection
