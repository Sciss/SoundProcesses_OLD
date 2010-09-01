package de.sciss.synth.proc.impl

import de.sciss.synth
import de.sciss.synth.proc.{ProcTxn, RichSynth, ProcSynthReaction, Ref, TxnPlayer }
import de.sciss.synth.osc.OSCResponder
import de.sciss.synth.{ scalar, control, Constant, GE, Rate, SynthGraph }
import de.sciss.synth.ugen.{ Impulse, Mix, SendReply }
import de.sciss.osc.OSCMessage

class SynthReactionImpl( trig: GE, values: GE, fun: Seq[ Double ] => Unit )
extends ProcSynthReaction {
   private val replyID = SynthGraph.individuate  // XXX best way to do this?

   // ---- constructor: embed in graph ----
   {
      import synth._
      val trig0 = trig match {
         case Constant( freq ) => Impulse( Rate.lowest( values ) match {
               case `scalar`  => control
               case r         => r
            }, freq, 0 )
         case _ => Mix( trig ) \ 0
      }
//println( "trig = " + trig0 )
      SendReply( Rate.highest( Rate.highest( values ), trig0.rate ) match {
         case `scalar`  => control
         case r         => r
      }, trig0, values.outputs, "/$react", Constant( replyID ))
   }

   private[proc] def create( rs: RichSynth )( implicit tx: ProcTxn ) : TxnPlayer = {
      val nodeID     = rs.node.id
      val resp       = OSCResponder {
         case OSCMessage( "/$react", `nodeID`, `replyID`, floats @ _* ) => {
            val doubles = floats.map( _.asInstanceOf[ Float ].toDouble )
            fun( doubles )
         }
//         case m => println( "<not> : " + m )
      }
      val player = new Player( resp )
//      tx.add( player )
      rs.onEnd { implicit tx => player.stop }
      player.play
      player
   }

   private class Player( resp: OSCResponder ) extends TxnPlayer {
      val playingRef = Ref.withCheck( false ) {
         case ply => if( ply ) {
//println( "---ADD" )
            resp.add
         } else {
//println( "---REMOVE" )
            resp.remove
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