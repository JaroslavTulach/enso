/** @file Configuration options for the application. */

import { logger } from 'runner/log'

export const DEFAULT_ENTRY_POINT = 'ide'

// =============
// === Utils ===
// =============

/** Parses the provided value as boolean. If it was a boolean value, it is left intact. If it was
 * a string 'true', 'false', '1', or '0', it is converted to a boolean value. Otherwise, null is
 * returned. */
// prettier-ignore
function parseBoolean(value: any): boolean | null {
    switch(value) {
        case true: return true
        case false: return false
        case 'true': return true
        case 'false': return false
        case 'enabled': return true
        case 'disabled': return false
        case 'yes': return true
        case 'no': return false
        case '1': return true
        case '0': return false
        default: return null
    }
}

// =============
// === Param ===
// =============

/** A valid parameter value. */
export type ParamValue = string | boolean | number | (string | null)

/** Configuration parameter. */
export class Param<T> {
    default: T
    value: T
    description: string
    setByUser = false
    constructor(value: T, description: string) {
        this.default = value
        this.value = value
        this.description = description
    }
}

// ==============
// === Params ===
// ==============

export type ExternalConfig = Record<string, ParamValue>

/** Application default configuration. Users of this library can extend it with their own
 * options. */
export class Params {
    [key: string]: Param<ParamValue>

    pkgWasmUrl = new Param<string>(
        'pkg.wasm',
        'The URL of the WASM pkg file generated by ensogl-pack.'
    )
    pkgJsUrl = new Param<string>('pkg.js', 'The URL of the JS pkg file generated by ensogl-pack.')
    shadersUrl = new Param<string>('shaders', 'The URL of the pre-compiled shaders directory.')
    entry = new Param<string>(
        DEFAULT_ENTRY_POINT,
        'The application entry point. Use `entry=_` to list available entry points.'
    )
    theme = new Param<string>('default', 'The EnsoGL theme to be used.')
    useLoader = new Param<boolean>(
        true,
        'Controls whether the visual loader should be visible on the screen when downloading and ' +
            'compiling WASM sources. By default, the loader is used only if the `entry` is set ' +
            `to ${DEFAULT_ENTRY_POINT}.`
    )
    loaderDownloadToInitRatio = new Param<number>(
        1.0,
        'The (time needed for WASM download) / (total time including WASM download and WASM app ' +
            'initialization). In case of small WASM apps, this can be set to 1.0. In case of ' +
            'bigger WASM apps, it is desired to show the progress bar growing up to e.g. 70% and ' +
            'leaving the last 30% for WASM app init.'
    )
    debug = new Param<boolean>(
        false,
        'Controls whether the application should be run in the debug mode. In this mode all logs ' +
            'are printed to the console. Otherwise, the logs are hidden unless explicitly shown ' +
            'by calling `showLogs`. Moreover, EnsoGL extensions are loaded in the debug mode ' +
            'which may cause additional logs to be printed.'
    )
    maxBeforeMainEntryPointsTimeMs = new Param<number>(
        300,
        'The maximum time in milliseconds a before main entry point is allowed to run. After ' +
            'this time, an error will be printed, but the execution will continue.'
    )
    enableSpector = new Param<boolean>(
        false,
        'Enables SpectorJS. This is a temporary flag to test Spector. It will be removed after ' +
            'all Spector integration issues are resolved. See: ' +
            'https://github.com/BabylonJS/Spector.js/issues/252.'
    )
}

// ==============
// === Config ===
// ==============

/** The configuration of the EnsoGL application. The options can be overriden by the user. The
 * implementation automatically casts the values to the correct types. For example, if an option
 * override for type boolean was provided as `'true'`, it will be parsed automatically. Moreover,
 * it is possible to extend the provided option list with custom options. See the `extend` method
 * to learn more. */
export class Config {
    params = new Params()

    constructor(config?: ExternalConfig) {
        this.extend(config)
    }

    /** Extend the configuration with user provided options. */
    extend(config?: ExternalConfig) {
        if (config != null) {
            Object.assign(this.params, config)
        }
    }

    /** Resolve the configuration from the provided record list.
     * @returns list of unrecognized parameters. */
    resolve(overrides: (Record<string, any> | undefined)[]): null | string[] {
        const allOverrides = {}
        for (const override of overrides) {
            if (override != null) {
                Object.assign(allOverrides, override)
            }
        }
        const unrecognizedParams = this.resolveFromObject(allOverrides)
        this.finalize()
        return unrecognizedParams
    }

    /** Resolve the configuration from the provided record.
     * @returns list of unrecognized parameters. */
    resolveFromObject(other: Record<string, any>): null | string[] {
        const paramsToBeAssigned = new Set(Object.keys(other))
        const invalidParams = new Set<string>()
        for (const key of Object.keys(this.params)) {
            paramsToBeAssigned.delete(key)
            const otherVal: unknown = other[key]
            const param = this.params[key]
            if (param == null) {
                invalidParams.add(key)
            } else {
                const selfVal = param.value
                if (otherVal != null) {
                    if (typeof selfVal === 'boolean') {
                        const newVal = parseBoolean(otherVal)
                        if (newVal == null) {
                            this.printValueUpdateError(key, selfVal, otherVal)
                        } else {
                            param.value = newVal
                            param.setByUser = true
                        }
                    } else if (typeof selfVal == 'number') {
                        const newVal = Number(otherVal)
                        if (isNaN(newVal)) {
                            this.printValueUpdateError(key, selfVal, otherVal)
                        } else {
                            param.value = newVal
                            param.setByUser = true
                        }
                    } else {
                        param.value = String(otherVal)
                        param.setByUser = true
                    }
                }
            }
        }
        if (paramsToBeAssigned.size > 0 || invalidParams.size > 0) {
            return [...paramsToBeAssigned, ...invalidParams]
        } else {
            return null
        }
    }

    /** Finalize the configuration. Set some default options based on the provided values. */
    finalize() {
        if (!this.params.useLoader.setByUser && this.params.entry.value !== DEFAULT_ENTRY_POINT) {
            this.params.useLoader.value = false
        }
    }

    printValueUpdateError(key: string, selfVal: any, otherVal: any) {
        console.error(
            `The provided value for Config.${key} is invalid. Expected boolean, got '${otherVal}'. \
            Using the default value '${selfVal}' instead.`
        )
    }

    strigifiedKeyValueMap(): Record<string, any> {
        const map: Record<string, any> = {}
        for (const [key, param] of Object.entries(this.params)) {
            if (param.value) {
                map[key] = param.value.toString()
            } else {
                map[key] = param.value
            }
        }
        return map
    }

    print() {
        logger.log(`Resolved config:`, this.strigifiedKeyValueMap())
    }
}
