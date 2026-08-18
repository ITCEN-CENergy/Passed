import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'

const normalizeJobPosting = (jobPosting) => ({
  jobPostingId: Number(jobPosting.jobPostingId ?? jobPosting.id),
  companyName: jobPosting.companyName ?? jobPosting.company?.name ?? '기업명 미정',
  title: jobPosting.title ?? jobPosting.jobPostingTitle ?? '채용 공고',
})

const useRoadmapBasketStore = create(
  persist(
    (set) => ({
      items: [],
      addItem: (jobPosting) => {
        const item = normalizeJobPosting(jobPosting)
        if (!item.jobPostingId) return

        set((state) => ({
          items: [
            ...state.items.filter((value) => value.jobPostingId !== item.jobPostingId),
            item,
          ],
        }))
      },
      removeItem: (jobPostingId) => set((state) => ({
        items: state.items.filter((item) => item.jobPostingId !== Number(jobPostingId)),
      })),
      clearItems: () => set({ items: [] }),
    }),
    {
      name: 'passed-roadmap-job-postings',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ items: state.items }),
    },
  ),
)

export default useRoadmapBasketStore
