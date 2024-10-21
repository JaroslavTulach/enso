/**
 * @file Type definitions for modules that currently lack typings on DefinitelyTyped.
 *
 * This file MUST NOT `export {}` so that the modules are visible to other files.
 */

// Required because this is a build artifact, which does not exist on a clean repository.
declare module '*/build.json' {
  /** Build metadata generated by the build CLI. */
  export interface BuildInfo {
    readonly commit: string
    readonly version: string
    readonly engineVersion: string
    readonly name: string
  }

  const BUILD_INFO: BuildInfo
  export default BUILD_INFO
}

declare module 'create-servers' {
  import type * as http from 'node:http'
  import type * as https from 'node:https'

  /** Configuration options for `create-servers`. */
  interface CreateServersOptions {
    readonly http?: number
    readonly handler: http.RequestListener
    // eslint-disable-next-line no-restricted-syntax
    readonly https?: {
      readonly port: number
      readonly key: string
      readonly cert: string
    }
  }

  /** An error passed to a callback when a HTTP request fails. */
  interface HttpError {
    readonly http: string
  }

  /** Created server instances of various types. */
  interface CreatedServers {
    readonly http?: http.Server
    readonly https?: https.Server
  }

  export default function (
    option: CreateServersOptions,
    // The types come from a third-party API and cannot be changed.
    // eslint-disable-next-line no-restricted-syntax
    handler: (err: HttpError | undefined, servers: CreatedServers) => void,
  ): unknown
}
