/** @file Test the login flow. */
import { expect, test, type Page } from '@playwright/test'

import { mockAllAndLogin } from './actions'

/** Find an editor container. */
function locateEditor(page: Page) {
  // Test ID of a placeholder editor component used during testing.
  return page.locator('.App')
}

/** Find a drive view. */
function locateDriveView(page: Page) {
  // This has no identifying features.
  return page.getByTestId('drive-view')
}

// FIXME[sb]: https://github.com/enso-org/cloud-v2/issues/1615
// Unskip once cloud execution in the browser is re-enabled.
test.skip('page switcher', ({ page }) =>
  mockAllAndLogin({ page })
    // Create a new project so that the editor page can be switched to.
    .newEmptyProjectTest()
    .do(async (thePage) => {
      await expect(locateDriveView(thePage)).not.toBeVisible()
      await expect(locateEditor(thePage)).toBeVisible()
    })
    .goToPage.drive()
    .do(async (thePage) => {
      await expect(locateDriveView(thePage)).toBeVisible()
      await expect(locateEditor(thePage)).not.toBeVisible()
    })
    .goToPage.editor()
    .do(async (thePage) => {
      await expect(locateDriveView(thePage)).not.toBeVisible()
      await expect(locateEditor(thePage)).toBeVisible()
    }))
