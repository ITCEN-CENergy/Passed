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

  const reload = useCallback(async ({ signal } = {}) => {
    setState((current) => ({ ...current, error: null, isLoading: true }))

    try {
      const data = await getCompanyCoverLetters({ signal })
      setState({ data, error: null, isLoading: false })
    } catch (error) {
      if (!isAbortError(error)) {
        setState((current) => ({ ...current, error, isLoading: false }))
      }
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    reload({ signal: controller.signal })
    return () => controller.abort()
  }, [reload])

  const remove = useCallback(async (coverLetterId) => {
    await deleteCompanyCoverLetter(coverLetterId)
    setState((current) => ({
      ...current,
      data: current.data?.filter((coverLetter) => coverLetter.id !== coverLetterId) ?? [],
    }))
  }, [])

  return {
    coverLetters: state.data ?? [],
    error: state.error,
    isLoading: state.isLoading,
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
