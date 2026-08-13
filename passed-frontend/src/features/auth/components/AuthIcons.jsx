const iconProps = {
  width: 24,
  height: 24,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
}

export const UserIcon = () => (
  <svg {...iconProps}>
    <circle cx="12" cy="8" r="4" />
    <path d="M4.5 20c.6-4 3.2-6 7.5-6s6.9 2 7.5 6" />
  </svg>
)

export const MailIcon = () => (
  <svg {...iconProps}>
    <rect x="3" y="5" width="18" height="14" rx="2" />
    <path d="m4 7 8 6 8-6" />
  </svg>
)

export const LockIcon = () => (
  <svg {...iconProps}>
    <rect x="4" y="10" width="16" height="11" rx="2" />
    <path d="M8 10V7a4 4 0 0 1 8 0v3" />
  </svg>
)

export const EyeIcon = ({ hidden = false }) => (
  <svg {...iconProps}>
    {hidden ? (
      <>
        <path d="M3 3l18 18" />
        <path d="M10.6 10.7a2 2 0 0 0 2.7 2.7" />
        <path d="M9.9 5.2A10.5 10.5 0 0 1 12 5c5.5 0 9 7 9 7a15.7 15.7 0 0 1-2.1 3.1" />
        <path d="M6.6 6.6C4.2 8.2 3 12 3 12s3.5 7 9 7a10.5 10.5 0 0 0 3.4-.6" />
      </>
    ) : (
      <>
        <path d="M3 12s3.5-7 9-7 9 7 9 7-3.5 7-9 7-9-7-9-7Z" />
        <circle cx="12" cy="12" r="2.5" />
      </>
    )}
  </svg>
)

export const ShieldIcon = () => (
  <svg {...iconProps}>
    <path d="M12 3 5 6v5c0 4.6 2.8 8 7 10 4.2-2 7-5.4 7-10V6l-7-3Z" />
    <path d="m9.5 12 1.7 1.7 3.6-4" />
  </svg>
)
