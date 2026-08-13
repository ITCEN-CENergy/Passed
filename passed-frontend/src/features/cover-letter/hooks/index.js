import { useCallback, useEffect, useState } from 'react'
import {
  deleteCompanyCoverLetter,
  getCompanyCoverLetter,
  getCompanyCoverLetters,
} from '../api'

const initialState = {
  data: null,
  error: null,
  isLoading: true,
}

function isAbortError(error) {
  return error?.name === 'AbortError'
}

export function useCompanyCoverLetters() {
  const [state, setState] = useState(initialState)
  const [page, setPage] = useState(0)

  const reload = useCallback(async ({ signal } = {}) => {
    setState((current) => ({ ...current, error: null, isLoading: true }))

    try {
      const data = await getCompanyCoverLetters({ page, signal })
      setState({ data, error: null, isLoading: false })
    } catch (error) {
      if (!isAbortError(error)) {
        setState((current) => ({ ...current, error, isLoading: false }))
      }
    }
  }, [page])

  useEffect(() => {
    const controller = new AbortController()
    reload({ signal: controller.signal })
    return () => controller.abort()
  }, [reload])

  const remove = useCallback(async (coverLetterId) => {
    await deleteCompanyCoverLetter(coverLetterId)
    setState((current) => ({
      ...current,
      data: current.data
        ? {
            ...current.data,
            content: current.data.content?.filter((coverLetter) => coverLetter.id !== coverLetterId) ?? [],
            totalElements: Math.max(0, (current.data.totalElements ?? 1) - 1),
          }
        : null,
    }))
  }, [])

  return {
    coverLetters: state.data?.content ?? (Array.isArray(state.data) ? state.data : []),
    error: state.error,
    isLoading: state.isLoading,
    page,
    totalPages: state.data?.totalPages ?? 1,
    goToPreviousPage: () => setPage((current) => Math.max(0, current - 1)),
    goToNextPage: () => setPage((current) => (
      current + 1 < (state.data?.totalPages ?? 1) ? current + 1 : current
    )),
    reload,
    remove,
  }
}

export function useCompanyCoverLetter(coverLetterId) {
  const [state, setState] = useState(initialState)

  const reload = useCallback(async ({ signal } = {}) => {
    if (!coverLetterId) {
      setState({ data: null, error: null, isLoading: false })
      return
    }

    setState((current) => ({ ...current, error: null, isLoading: true }))

    try {
      const data = await getCompanyCoverLetter(coverLetterId, { signal })
      setState({ data, error: null, isLoading: false })
    } catch (error) {
      if (!isAbortError(error)) {
        setState((current) => ({ ...current, error, isLoading: false }))
      }
    }
  }, [coverLetterId])

  useEffect(() => {
    const controller = new AbortController()
    reload({ signal: controller.signal })
    return () => controller.abort()
  }, [reload])

  return {
    coverLetter: state.data,
    error: state.error,
    isLoading: state.isLoading,
    reload,
  }
}
