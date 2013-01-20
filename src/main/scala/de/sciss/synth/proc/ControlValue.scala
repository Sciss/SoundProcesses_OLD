/*
 *  ControlValue.scala
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

package de.sciss.synth.proc

object ControlValue {
   def instant( value: Double ) = ControlValue( value, None )
}

case class ControlValue( target: Double, mapping: Option[ ControlMapping ]) {
   def current( implicit txn: ProcTxn ) : Double = mapping match {
      case Some( cg: ControlGliding ) => cg.currentValue
      case _ => target
   }

   def instant = ControlValue( target, None )

   /**
    *    Transaction-less coarse approximation
    *    of the current value.  Useful for a GUI
    *    which does not want to mess around with
    *    txn.
    */
   def currentApprox : Double = mapping match {
      case Some( cg: ControlGliding ) => cg.currentValueApprox
      case _ => target
   }
}

sealed trait ControlMapping extends TxnPlayer {
   def mapBus( implicit tx: ProcTxn ) : RichBus
}

trait ControlGliding extends ControlMapping {
   def startNorm : Double
   def targetNorm : Double
   def ctrl: ProcControl
   def glide : Glide

   def currentNorm( implicit tx: ProcTxn ) : Double = {
      val w = glide.position( tx.time )
      startNorm * (1 - w) + targetNorm * w
   }

   def currentNormApprox : Double = {
      val w = glide.positionApprox
      startNorm * (1 - w) + targetNorm * w
//println( "currentNormApprox : pos = " + w + "; startNorm = " + startNorm + "; targetNorm = " + targetNorm +" ; res = " +res )
//      res
   }

   def startValue    = ctrl.spec.map( startNorm )
   def targetValue   = ctrl.spec.map( targetNorm )

   def currentValue( implicit tx: ProcTxn ) : Double = ctrl.spec.map( currentNorm )
   def currentValueApprox : Double = ctrl.spec.map( currentNormApprox )
}

sealed trait ControlBusMapping extends ControlMapping {

}

trait ControlABusMapping extends ControlBusMapping {
//   def edge : ProcEdge// ( implicit tx: ProcTxn )
   def in: ProcAudioInput
   def out: ProcAudioOutput
}

//trait ControlKBusMapping extends ControlBusMapping {
//
//}