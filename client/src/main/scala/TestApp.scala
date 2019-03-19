import scala.scalajs.js.annotation._

@JSExportTopLevel("TutorialApp")
object TutorialApp {
  @JSExport
  def sayHello(): Unit = {
    println("Hello, scala! from scala.js")
  }
}
