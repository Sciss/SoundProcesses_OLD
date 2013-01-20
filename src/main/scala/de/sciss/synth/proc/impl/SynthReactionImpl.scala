/*
 *  SynthReactionImpl.scala
 *  (SoundProcesses)
 *
 *  Copyright (c) 2010-2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.proc.impl

import de.sciss.synth
import de.sciss.synth.proc.{ProcTxn, RichSynth, ProcSynthReaction, Ref, TxnPlayer }
import de.sciss.synth.{osc => sosc}
import de.sciss.synth.GE
import de.sciss.synth.ugen.{ Impulse, Mix, SendReply }
import de.sciss.osc.Message

class SynthReactionImpl( trig: GE, values: GE, fun: Seq[ Double ] => Unit, replyID: Int )
extends ProcSynthReaction {
//   private val replyID = SynthGraph.individuate  // XXX best way to do this?

   // ---- constructor: embed in graph ----
   {
      import synth._
      val trig0 = trig match {
         case Constant( freq ) => Impulse( values.rate match {
               case `scalar`        => control
               case r: Rate         => r
               case UndefinedRate   => audio
            }, freq, 0 )
         case _ => Mix.mono( trig )
      }
//println( "trig = " + trig0 )
      SendReply( trig0.rate, trig0, values /* .outputs */, "/$react", Constant( replyID ))
   }

   private[proc] def create( rs: RichSynth )( implicit tx: ProcTxn ) : TxnPlayer = {
      val nodeID     = rs.node.id
      val resp       = sosc.Responder( rs.server ) {
         case Message( "/$react", `nodeID`, `replyID`, floats @ _* ) => {
            val doubles = floats.map( _.asInstanceOf[ Float ].toDouble )
            fun( doubles )
         }
//         case m => println( "<not> : " + m )
      }
      val player = new Player( resp )
      rs.onEndTxn { implicit tx => player.stop }
      player.play
      player
   }

   private class Player( resp: sosc.Responder ) extends TxnPlayer {
      val playingRef = Ref.withCheck( false ) {
         case ply => if( ply ) {
//println( "---ADD" )
            resp.add()
         } else {
//println( "---REMOVE" )
            resp.remove()
         }
      }

      def play( implicit tx: ProcTxn ) {
         playingRef.set( true )
//         val wasPlaying = playingRef.swap( true )
//         if( !wasPlaying ) {
//            touch // resp.add
//         }
      }
      def stop( implicit tx: ProcTxn ) {
         playingRef.set( false )
//         val wasPlaying = playingRef.swap( false )
//         if( wasPlaying ) touch // resp.remove
      }

      def isPlaying( implicit tx: ProcTxn ) : Boolean = playingRef()
   }
}