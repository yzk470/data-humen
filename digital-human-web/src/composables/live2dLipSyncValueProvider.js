import { IParameterProvider } from '../cubism-framework/motion/iparameterprovider'

export class Live2dLipSyncValueProvider extends IParameterProvider {
  constructor() {
    super()
    this.value = 0
  }

  setValue(value) {
    if (typeof value !== 'number' || Number.isNaN(value)) {
      this.value = 0
      return
    }

    this.value = Math.max(0, Math.min(1, value))
  }

  update() {
    return true
  }

  getParameter() {
    return this.value
  }
}
