const STORAGE_KEY = 'passed-roadmap-job-postings'
export const BASKET_CHANGE_EVENT = 'passed:roadmap-basket-change'

export const getRoadmapBasket = () => {
  try {
    const value = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || '[]')
    return Array.isArray(value) ? value.filter(item => Number(item?.jobPostingId) > 0) : []
  } catch {
    return []
  }
}

const save = (items) => {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
  window.dispatchEvent(new CustomEvent(BASKET_CHANGE_EVENT, { detail: items }))
  return items
}

export const addToRoadmapBasket = (jobPosting) => {
  const item = {
    jobPostingId: Number(jobPosting.jobPostingId ?? jobPosting.id),
    companyName: jobPosting.companyName ?? jobPosting.company?.name ?? '기업명 미정',
    title: jobPosting.title ?? jobPosting.jobPostingTitle ?? '채용 공고',
  }
  if (!item.jobPostingId) return getRoadmapBasket()
  return save([...getRoadmapBasket().filter(value => value.jobPostingId !== item.jobPostingId), item])
}

export const removeFromRoadmapBasket = (jobPostingId) => save(getRoadmapBasket().filter(item => item.jobPostingId !== jobPostingId))
export const clearRoadmapBasket = () => save([])
