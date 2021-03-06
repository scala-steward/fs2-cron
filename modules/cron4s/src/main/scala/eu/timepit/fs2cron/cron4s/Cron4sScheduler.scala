/*
 * Copyright 2018-2021 fs2-cron contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.timepit.fs2cron.cron4s

import cats.effect.{Sync, Temporal}
import cats.syntax.all._
import cron4s.expr.CronExpr
import cron4s.lib.javatime._
import eu.timepit.fs2cron.Scheduler

import java.time.temporal.ChronoUnit
import java.time.{ZoneId, ZoneOffset, ZonedDateTime}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object Cron4sScheduler {
  def systemDefault[F[_]](implicit temporal: Temporal[F], F: Sync[F]): Scheduler[F, CronExpr] =
    from(F.delay(ZoneId.systemDefault()))

  def utc[F[_]](implicit F: Temporal[F]): Scheduler[F, CronExpr] =
    from(F.pure(ZoneOffset.UTC))

  def from[F[_]](zoneId: F[ZoneId])(implicit F: Temporal[F]): Scheduler[F, CronExpr] =
    new Scheduler[F, CronExpr] {
      override def fromNowUntilNext(schedule: CronExpr): F[FiniteDuration] =
        now.flatMap { from =>
          schedule.next(from) match {
            case Some(next) =>
              val durationInMillis = from.until(next, ChronoUnit.MILLIS)
              F.pure(FiniteDuration(durationInMillis, TimeUnit.MILLISECONDS))
            case None =>
              val msg = s"Could not calculate the next date-time from $from " +
                s"given the cron expression '$schedule'. This should never happen."
              F.raiseError(new Throwable(msg))
          }
        }

      override def temporal: Temporal[F] = F

      private val now: F[ZonedDateTime] =
        (F.realTimeInstant, zoneId).mapN(_.atZone(_))
    }
}
