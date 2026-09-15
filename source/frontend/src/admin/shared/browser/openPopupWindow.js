export function openPopupWindow(url, options = {}) {
  const width = Math.min(Number(options.width) || 1500, window.screen.availWidth)
  const height = Math.min(Number(options.height) || 950, window.screen.availHeight)
  const left = Math.max(0, Math.round((window.screen.availWidth - width) / 2))
  const top = Math.max(0, Math.round((window.screen.availHeight - height) / 2))
  const name = options.name || 'adminPopupWindow'
  const features = [
    'popup=yes',
    `width=${width}`,
    `height=${height}`,
    `left=${left}`,
    `top=${top}`,
    'resizable=yes',
    'scrollbars=yes',
  ].join(',')

  const popup = window.open(url, name, features)
  popup?.focus()
  return Boolean(popup)
}

