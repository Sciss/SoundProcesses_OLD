/*
 *  SoundProcesses.scala
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

import ProcTxn.{ atomic => t }
import DSL._
import de.sciss.synth._
import de.sciss.synth.ugen._

object SoundProcesses {
   val name          = "SoundProcesses"
   val version       = "0.35.0"
   val copyright     = "(C)opyright 2010-2013 Hanns Holger Rutz"

   def main( args: Array[ String ]) {
      (if( args.size > 0 ) args( 0 ) else "") match {
         case "--test1" => test1()
         case "--test2" => test2()
         case "--test3" => test3()
         case "--test4" => test4()
         case "--test5" => test5()
         case "--test6" => test6()
         case "--test7" => test7()
         case _ =>
            printInfo()
            sys.exit( 1 )
      }
   }

   def printInfo() {
      println( "\n" + name + " v" + version + "\n" + copyright + ". All rights reserved.\n" +
         "This is a library which cannot be executed directly.\n" )
   }

//   def test2 {
//      case class Edge( sourceVertex: String, targetVertex: String ) extends Topology.Edge[ String ]
//      val topo1 = Topology.empty[ String, Edge ]
//      val topo2 = topo1.addVertex( "A" )
//      val topo3 = topo2.addVertex( "B" )
//      val Some( (topo4, source1, affected1) ) = topo3.addEdge( Edge( "A", "B" ))
//      val topo4b = topo4.addVertex( "D" )
//      val topo5 = topo4b.addVertex( "C" )
//      val Some( (topo6, source2, affected2) ) = topo5.addEdge( Edge( "A", "C" ))
//      val topo7 = topo6.removeEdge( Edge( "A", "B" ))
//      val Some( (topo8, source3, affected3) ) = topo7.addEdge( Edge( "C", "B" ))
//      // removal
//      val topo9 = topo8.removeEdge( Edge( "A", "C" ))
//      val topo10 = topo9.removeEdge( Edge( "C", "B" ))
//      val Some( (topo11, source4, affected4) ) = topo10.addEdge( Edge( "A", "B" ))
//      val topo12 = topo11.removeVertex( "C" )
//      val topo13 = topo12.removeVertex( "D" )
//      println( "done" )
//   }

   private def boot( code: => Unit ) {
      Server.run { s =>
         s.dumpOSC()
         ProcDemiurg.addServer( s )
         code
      }
   }

   private def test1() {
      t { implicit tx =>
         /* val disk = */ gen( "Disk" ) {
            val pspeed  = pControl( "speed", ParamSpec( 0.1f, 10, ExpWarp ), 1 )
            val ppos    = pScalar( "pos",  ParamSpec( 0, 1 ), 0 )
            graph {
               val path       = "/Users/rutz/Desktop/Cupola/audio_work/material/lalaConlalaQuadNoAtkSt.aif"
               val afSpec     = audioFileSpec( path )
               val startPos   = ppos.v
               val startFrame = (startPos * afSpec.numFrames).toLong
               val buf        = bufCue( path, startFrame )
               val bufID      = buf.id
               val speed      = pspeed.kr * BufRateScale.ir( bufID )
               val d          = VDiskIn.ar( afSpec.numChannels, bufID, speed, loop = 1 )
//               val frame   = d.reply
//               (frame.carry( pspeed.v * b.sampleRate ) / b.numFrames) ~> ppos
//               val liveFrame  = Integrator.ar( K2A.ar( speed ))
//               val livePos    = ((liveFrame / BufFrames.ir( bufID )) + startPos) % 1.0f
//               livePos ~> ppos
               d
            }
         }

         /* val achil = */ filter( "Achil") {
            val pspeed  = pAudio( "speed", ParamSpec( 0.125, 2.3511, ExpWarp ), 0.5 )
            val pmix    = pAudio( "mix", ParamSpec( 0, 1 ), 1 )

            graph { in: In =>
               val speed	   = Lag.ar( pspeed.ar, 0.1 )
               val numFrames  = sampleRate.toInt
               val numChannels= in.numChannels // numOutputs
               val buf        = bufEmpty( numFrames, numChannels )
               val bufID      = buf.id
               val writeRate  = BufRateScale.kr( bufID )
               val readRate   = writeRate * speed
               val readPhasor = Phasor.ar( 0, readRate, 0, numFrames )
               val read			= BufRd.ar( numChannels, bufID, readPhasor, 0, 4 )
               val writePhasor= Phasor.ar( 0, writeRate, 0, numFrames )
               val old			= BufRd.ar( numChannels, bufID, writePhasor, 0, 1 )
               val wet0 		= SinOsc.ar( 0, ((readPhasor - writePhasor).abs / numFrames * math.Pi) )
               val dry			= 1 - wet0.squared
               val wet			= 1 - (1 - wet0).squared
               BufWr.ar( (old * dry) + (in * wet), bufID, writePhasor )
               LinXFade2.ar( in, read, pmix.ar * 2 - 1 )
            }
         }
      }
   }

   private def test2() {
      boot {
         val (p1, p2) = t { implicit tx =>
            val p1 = (gen( "Mod" ) {
               graph { SinOsc.ar( 2 )}
            }).make
            val p3 = (diff( "Silent" ) {
               graph { _: In => Silent.ar }
            }).make
            val p2 = (gen( "Osc" ) {
               val pfreq = pAudio( "freq", ParamSpec( 100, 10000, ExpWarp ), 441 )
               graph { SinOsc.ar( pfreq.ar ) * 0.1 }
            }).make
            p1 ~> p3
            p1.play; p2.play; p3.play
            (p1, p2)
         }

         println( "\n-------------\n" )

         t { implicit tx =>
            xfade( 15 ) {
               p1 ~> p2.control( "freq" )
            }
         }

//         t { implicit tx =>
//            xfade( 15 ) {
//               p1 ~/> p2.control( "freq" )
//            }
//         }
      }
   }

   private def test3() {
      boot {
         t { implicit tx =>
            val genDummyStereo = gen( "@" ) { graph { Silent.ar( 2 )}}
            val fieldCollectors: Map[ Int, Proc ] = (1 to 4).map( field => {
               val genColl = filter( field.toString ) { graph { in: GE => in }}
               val pColl   = genColl.make
               val pDummy  = genDummyStereo.make
               pDummy ~> pColl
               pColl.play
//               pDummy.dispose
               field -> pColl
            })( collection.breakOut )
            val sub        = (fieldCollectors - 4).values
            val collMaster = fieldCollectors( 4 )
            sub foreach { _ ~> collMaster }

            // ---- master ----

            val pMaster = diff( "master" )( graph { in: GE =>
               val ctrl = HPF.ar( in, 50 )
               val cmp  = Compander.ar( in, ctrl, (-12).dbamp, 1, 1.0/3.0 ) * 2
               Out.ar( 0, cmp )
            }).make
//            val topo1 = ProcDemiurg.worlds( s ).topology
            collMaster ~> pMaster
//            val topo2 = ProcDemiurg.worlds( s ).topology
            pMaster.play
//            s.dumpOSC()
//            val g1 = collMaster.groupOption
//            val g2 = pMaster.groupOption
            println( "JA" )
         }
      }
   }

   private def test4() {
      boot {
         t { implicit tx =>
            val p1 = gen( "1" )({
               graph { PinkNoise.ar( List( 0.2, 0.2 ))}
            }).make
            val p2 = diff( "2" )({
               graph { in: GE => Out.ar( 0, in )}
            }).make
            val p3 = gen( "3" )({
               graph { SinOsc.ar( List( 400, 410 )) * 0.2 }
            }).make
            p1 ~> p2
            p1.play; p2.play

            xfade( 15 ) {
               p1.stop
               p3 ~> p2
               p3.play
            }

//            val g1 = p1.groupOption
//            val g2 = p2.groupOption
//            val g3 = p3.groupOption
            println( "JA" )
         }
      }
   }

   private def test5() {
      boot {
         t { implicit tx =>
            val p = (gen( "test" ) {
               graph {
                  val in = In.ar( NumOutputBuses.ir, 2 )
                  val fft = FFT( bufEmpty( 1024 ).id, Mix( in ))
                  val spec = SpecPcile.kr( fft )
                  val smooth = Lag.kr( spec, 10 )
                  2f.react( smooth ) { data =>
                     val Seq( freq ) = data
                     println( "GOT: " + freq )
                  }
                  0 // in
               }
            }).make
            p.play
         }
         println( "Ok" )
      }
   }

   private def test6() {
      boot {
         t { implicit tx =>
            val f1 = gen( "gen" ) {
               graph {
//println( "AQUI : Pink" )
                  PinkNoise.ar( Seq( 0.2, 0.2 ))
               }
            }
//            val f2 = diff( "diff" ) {
//               graph { in: In =>
//println( "AQUI : Out" )
//                  Out.ar( 0, in )
//               }
//            }
            val p1 = f1.make
//            val p2 = f2.make
//            p1 ~> p2
            p1.play
//            p2.play
         }
         println( "Ok" )
      }
   }

   private def test7() {
      Server.run { s =>
         ProcDemiurg.addServer( s )
         val (g, f, d) = t { implicit tx =>
            val gf = gen( "Sprink" ) {
               graph {
                  Dust.ar( Seq( 200, 400 ))
               }
            }

            val ff = filter( "Filt" ) {
               graph { in: In =>
                  val hpf = HPF.ar( in, 8000 )
                  hpf
               }
            }

            val df = diff( "Out" ) {
                val pout  = pAudioOut( "out", Some( RichBus.soundOut( s, 2 )))
                graph { in: In =>
                   pout.ar( in )
                }
            }

            val g = gf.make
            val d = df.make
            val f = ff.make
            g ~> d
//            g.play
            d.play
            (g, f, d)
         }

         t { implicit tx =>
            g ~| f |> d
            f.play
         }
      }
   }
}