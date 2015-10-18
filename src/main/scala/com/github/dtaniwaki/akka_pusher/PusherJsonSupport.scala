package com.github.dtaniwaki.akka_pusher

import spray.json._
import com.github.nscala_time.time.Imports._
import org.joda.time.format._

trait PusherJsonSupport extends DefaultJsonProtocol {
  import PusherRequests._
  import PusherModels._
  import PusherEvents._

  private implicit object DateTimeJsonFormat extends RootJsonFormat[DateTime] {
    private lazy val format = ISODateTimeFormat.dateTimeNoMillis()

    def write(datetime: DateTime): JsValue = JsString(format.print(datetime.withZone(DateTimeZone.UTC)))

    def read(json: JsValue): DateTime = json match {
      case JsString(x) => format.parseDateTime(x)
      case x           => deserializationError("Expected DateTime as JsString, but got " + x)
    }
  }

  implicit val ChannelOccupiedEventJsonSupport = jsonFormat(ChannelOccupiedEvent.apply, "name", "channel")
  implicit val ChannelVacatedEventJsonSupport = jsonFormat(ChannelVacatedEvent.apply, "name", "channel")
  implicit val MemberAddedEventJsonSupport = jsonFormat(MemberAddedEvent.apply, "name", "channel", "user_id")
  implicit val MemberRemovedEventJsonSupport = jsonFormat(MemberRemovedEvent.apply, "name", "channel", "user_id")

  implicit object ClientEventJsonSupport extends RootJsonFormat[ClientEvent] {
    def write(event: ClientEvent): JsValue = {
      JsObject(
        "name" -> JsString(event.name),
        "channel" -> JsString(event.channel),
        "user_id" -> JsString(event.userId),
        "event" -> JsString(event.event),
        "data" -> event.data.toJson,
        "socket_id" -> JsString(event.socketId)
      )
    }
    def read(json: JsValue): ClientEvent = {
      json.asJsObject.getFields("name", "channel", "user_id", "event", "data", "socket_id") match {
        case Seq(JsString(name), JsString(channel), JsString(userId), JsString(event), data, JsString(socketId)) =>
          ClientEvent(name, channel, userId, event, data.convertTo[Map[String, String]], socketId)
      }
    }
  }

  implicit def ChannelDataJsonSupport[T : JsonFormat] = new JsonFormat[ChannelData[T]] {
    override def write(data: ChannelData[T]): JsValue =
      data.userInfo.map { info =>
        JsObject(
          "user_id" -> JsString(data.userId),
          "user_info" -> info.toJson
        )
      }.getOrElse {
        JsObject("user_id" -> JsString(data.userId))
      }

    override def read(json: JsValue): ChannelData[T] =
      json.asJsObject.getFields("user_id", "user_info") match {
        case Seq(JsString(userId), userInfo) =>
          ChannelData(userId, Some(userInfo.convertTo[T]))
        case Seq(JsString(userId), JsNull) =>
          ChannelData[T](userId)
        case x => deserializationError("ChannelData is expected: " + x)
      }
  }

  implicit val AuthRequestJsonSupport = jsonFormat(AuthRequest.apply, "socket_id", "channel_name")
  implicit object WebhookRequestJsonSupport extends RootJsonFormat[WebhookRequest] {
    def write(res: WebhookRequest): JsValue = {
      val events = res.events.map {
        case event: ChannelOccupiedEvent => event.toJson
        case event: ChannelVacatedEvent => event.toJson
        case event: MemberAddedEvent => event.toJson
        case event: MemberRemovedEvent => event.toJson
        case event: ClientEvent => event.toJson
      }.toVector
      JsObject(
        "time_ms" -> JsNumber((res.timeMs.getMillis / 1000).toLong),
        "events" -> JsArray(events)
      )
    }
    def read(json: JsValue): WebhookRequest = {
      json.asJsObject.getFields("time_ms", "events") match {
        case Seq(JsNumber(timeMs), JsArray(events)) =>
          WebhookRequest(new DateTime(timeMs.toLong * 1000), events.map { event =>
            event.asJsObject.getFields("name")(0) match {
              case JsString("client_event")     => event.convertTo[ClientEvent]
              case JsString("channel_occupied") => event.convertTo[ChannelOccupiedEvent]
              case JsString("channel_vacated")  => event.convertTo[ChannelVacatedEvent]
              case JsString("member_added")     => event.convertTo[MemberAddedEvent]
              case JsString("member_removed")   => event.convertTo[MemberRemovedEvent]
              case _ => null
            }
          }.filter(_ != null))
        case x => deserializationError("WebhookRequest is expected: " + x)
      }
    }
  }

  implicit val ResultJsonSupport = jsonFormat1(Result)
  implicit val ChannelsJsonSupport = jsonFormat1(Channels)
  implicit val ChannelJsonSupport = jsonFormat1(Channel)
  implicit val UsersJsonSupport = jsonFormat1(Users)
  implicit val AuthenticatedParamsJsonSupport = jsonFormat(AuthenticatedParams.apply, "auth", "channel_data")
}
