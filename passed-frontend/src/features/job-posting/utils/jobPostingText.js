export const splitJobPostingSentences = (value) => String(value ?? '')
  .split(/[?？]+|\r?\n+|[•●○◦▪︎■◆]+/)
  .map((sentence) => sentence.trim().replace(/^[•·●○◦▪︎■◆\-–—]+\s*/, ''))
  .filter(Boolean)

export const formatJobPostingParagraph = (value) => (
  splitJobPostingSentences(value).join(' ')
)

export const formatJobPostingList = (value) => (
  splitJobPostingSentences(value).map((sentence) => `• ${sentence}`).join('\n')
)
