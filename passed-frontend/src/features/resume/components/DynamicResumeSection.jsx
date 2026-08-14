import styles from './DynamicResumeSection.module.css'

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
  return <input type={field.type || 'text'} value={value ?? ''} placeholder={field.placeholder} required={field.required} onChange={(event) => onChange(event.target.value)} />
}

const DynamicResumeSection = ({ description, fields, items, onAdd, onChange, onRemove, title }) => (
  <section className={styles.section}>
    <header>
      <div><h2>{title}</h2><p>{description}</p></div>
      <button type="button" onClick={onAdd}>+ 항목 추가</button>
    </header>
    {!items.length ? (
      <button className={styles.empty} type="button" onClick={onAdd}>+ {title} 정보를 추가해주세요</button>
    ) : (
      <div className={styles.items}>
        {items.map((item, index) => (
          <article key={item.clientId}>
            <div className={styles.itemTitle}><strong>{title} {index + 1}</strong><button type="button" onClick={() => onRemove(index)}>삭제</button></div>
            <div className={styles.grid}>
              {fields.map((field) => (
                <label className={field.wide ? styles.wide : ''} key={field.name}>
                  <span>{field.label}{field.required && <b> *</b>}</span>
                  {fieldControl(field, item[field.name], (value) => onChange(index, field.name, value))}
                </label>
              ))}
            </div>
          </article>
        ))}
      </div>
    )}
  </section>
)

export default DynamicResumeSection
