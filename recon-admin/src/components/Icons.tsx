/** Small inline SVG set. No icon font, no emoji: both change shape between platforms. */
const base = {
  width: 15,
  height: 15,
  viewBox: '0 0 16 16',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.4,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
}

export const IconDashboard = () => (
  <svg {...base}><rect x="2" y="2" width="5" height="5" /><rect x="9" y="2" width="5" height="5" /><rect x="2" y="9" width="5" height="5" /><rect x="9" y="9" width="5" height="5" /></svg>
)
export const IconRuns = () => (
  <svg {...base}><path d="M2 8h3l2-4 2 8 2-4h3" /></svg>
)
export const IconDiff = () => (
  <svg {...base}><path d="M8 2v12M2 5h4M10 11h4" /><circle cx="8" cy="8" r="6" /></svg>
)
export const IconSettlement = () => (
  <svg {...base}><rect x="2.5" y="2" width="11" height="12" /><path d="M5 5.5h6M5 8h6M5 10.5h3.5" /></svg>
)
export const IconReprocess = () => (
  <svg {...base}><path d="M13.5 8a5.5 5.5 0 1 1-1.9-4.2" /><path d="M13.5 2v3.5H10" /></svg>
)
export const IconIngest = () => (
  <svg {...base}><path d="M8 10.5V2.5M5 5.5 8 2.5l3 3" /><path d="M2.5 10.5v3h11v-3" /></svg>
)
export const IconAudit = () => (
  <svg {...base}><circle cx="7" cy="7" r="4.5" /><path d="M10.5 10.5 14 14" /></svg>
)
export const IconChannel = () => (
  <svg {...base}><circle cx="8" cy="4" r="2" /><circle cx="3.5" cy="12" r="2" /><circle cx="12.5" cy="12" r="2" /><path d="M8 6v3M8 9 4.5 10.5M8 9l3.5 1.5" /></svg>
)
export const IconMark = () => (
  <svg width="17" height="17" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
    <path d="M2 4.5h5.5M8.5 4.5H14M2 11.5h5.5M8.5 11.5H14" />
    <circle cx="8" cy="8" r="2.2" />
  </svg>
)
