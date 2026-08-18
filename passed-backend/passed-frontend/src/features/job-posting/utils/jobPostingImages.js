const COMPANY_IMAGES = [
  ['company_building1.jpg', 'company_inner.jpg', 'company_office9.jpg'],
  ['company_building2.jpg', 'company_inner7.jpg', 'company_office17.jpg'],
  ['company_building3.jpg', 'company_inner6.jpg', 'company_office3.jpg'],
  ['company_building4.jpg', 'company_inner4.jpg', 'company_office19.jpg'],
  ['company_building5.jpg', 'company_inner8.jpg', 'company_office15.jpg'],
  ['company_inner2.jpg', 'company_office11.jpg', 'company_office4.jpg'],
  ['company_inner3.jpg', 'company_office12.jpg', 'company_office5.jpg'],
  ['company_inner5.jpg', 'company_office6.jpg', 'company_office7.jpg'],
  ['company_inner9.jpg', 'company_office1.jpg', 'company_office2.jpg'],
  ['company_inner10.jpg', 'company_office13.jpg', 'company_office14.jpg'],
  ['company_inner11.jpg', 'company_office10.jpg', 'company_office8.jpg'],
  ['company_inner12.jpg', 'company_office16.jpg', 'company_office18.jpg'],
]

const numericSeed = (value) => {
  const text = String(value ?? '')
  return [...text].reduce((sum, character, index) => (
    sum + character.charCodeAt(0) * (index + 7)
  ), 0)
}

export const getJobPostingImage = (jobPostingId, listIndex = 0) => {
  const seed = numericSeed(jobPostingId)
  const folderIndex = Math.abs((Number(listIndex) || 0) + seed) % COMPANY_IMAGES.length
  const fileIndex = Math.abs(seed + folderIndex * 5) % COMPANY_IMAGES[folderIndex].length
  return `/company_img${folderIndex + 1}/${COMPANY_IMAGES[folderIndex][fileIndex]}`
}

export const getUniqueJobPostingImages = (jobPostings = []) => jobPostings.map((jobPosting, index) => {
  const folderIndex = index % COMPANY_IMAGES.length
  const fileIndex = numericSeed(jobPosting.jobPostingId) % COMPANY_IMAGES[folderIndex].length
  return `/company_img${folderIndex + 1}/${COMPANY_IMAGES[folderIndex][fileIndex]}`
})
