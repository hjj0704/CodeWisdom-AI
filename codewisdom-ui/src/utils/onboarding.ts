const NEW_USER_KEY = 'cw_onboarding_new_user'
const IMPORT_DONE_KEY = 'cw_onboarding_import_done'
const WORKBENCH_DONE_KEY = 'cw_onboarding_workbench_done'
const ALL_DONE_KEY = 'cw_onboarding_v1_done'

export function markOnboardingForNewUser() {
  localStorage.setItem(NEW_USER_KEY, '1')
  localStorage.removeItem(IMPORT_DONE_KEY)
  localStorage.removeItem(WORKBENCH_DONE_KEY)
  localStorage.removeItem(ALL_DONE_KEY)
}

export function isNewUser(): boolean {
  return localStorage.getItem(NEW_USER_KEY) === '1' && localStorage.getItem(ALL_DONE_KEY) !== '1'
}

export function isImportTourPending(): boolean {
  return isNewUser() && localStorage.getItem(IMPORT_DONE_KEY) !== '1'
}

export function isWorkbenchTourPending(): boolean {
  return isNewUser() && localStorage.getItem(WORKBENCH_DONE_KEY) !== '1'
}

export function completeImportTour() {
  localStorage.setItem(IMPORT_DONE_KEY, '1')
  if (localStorage.getItem(WORKBENCH_DONE_KEY) === '1') {
    localStorage.setItem(ALL_DONE_KEY, '1')
    localStorage.removeItem(NEW_USER_KEY)
  }
}

export function completeWorkbenchTour() {
  localStorage.setItem(WORKBENCH_DONE_KEY, '1')
  if (localStorage.getItem(IMPORT_DONE_KEY) === '1') {
    localStorage.setItem(ALL_DONE_KEY, '1')
    localStorage.removeItem(NEW_USER_KEY)
  }
}

export function skipAllOnboarding() {
  localStorage.setItem(ALL_DONE_KEY, '1')
  localStorage.setItem(IMPORT_DONE_KEY, '1')
  localStorage.setItem(WORKBENCH_DONE_KEY, '1')
  localStorage.removeItem(NEW_USER_KEY)
}

/** @deprecated 使用 skipAllOnboarding */
export function completeOnboarding() {
  skipAllOnboarding()
}

export function isOnboardingPending(): boolean {
  return isImportTourPending() || isWorkbenchTourPending()
}
