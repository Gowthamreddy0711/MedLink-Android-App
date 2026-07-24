import { useState, useEffect } from 'react';
import { motion } from 'motion/react';
import { useNavigate } from 'react-router-dom';
import { 
  Users, 
  Calendar, 
  Clock, 
  Activity, 
  AlertCircle, 
  ChevronRight, 
  CheckCircle2, 
  Settings, 
  Bell, 
  Building, 
  FileText,
  Shield, 
  Flame, 
  MapPin, 
  Award,
  BookOpen
} from 'lucide-react';
import { ROUTES } from '../../constants';
import { cn } from '../../lib/utils';
import { db } from '../../services/db';
import { collection, query, where, onSnapshot } from 'firebase/firestore';
import { db as firestoreDb } from '../../services/firebase';

export default function DoctorDashboard({ user }: { user: any }) {
  const navigate = useNavigate();
  const [appointmentsCount, setAppointmentsCount] = useState<number>(0);
  const [appointments, setAppointments] = useState<any[]>([]);
  const [appointmentsLoading, setAppointmentsLoading] = useState(true);
  const [queue, setQueue] = useState<any[]>([]);
  const [queueLoading, setQueueLoading] = useState(true);

  const doctorId = user?.id || 'd1';

  useEffect(() => {
    if (!doctorId) {
      setAppointmentsLoading(false);
      return;
    }
    setAppointmentsLoading(true);

    const qAppts = query(collection(firestoreDb, 'appointments'), where('doctorId', '==', doctorId));

    // Listen in real-time to Firestore appointments
    const unsubscribe = onSnapshot(qAppts, (snapshot) => {
      const liveAppts = snapshot.docs.map(doc => doc.data());
      // Merge with local storage cache to support offline or local fallback seamlessly
      const localApptsStr = localStorage.getItem('medlink_local_appointments');
      const localAppts = (localApptsStr ? JSON.parse(localApptsStr) : []).filter((a: any) => a.doctorId === doctorId);

      const merged = [...liveAppts];
      for (const a of localAppts) {
        if (!merged.some(existing => existing.id === a.id)) {
          merged.push(a);
        }
      }

      setAppointmentsCount(merged.length);
      const sorted = merged.sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
      setAppointments(sorted.slice(0, 3));
      setAppointmentsLoading(false);
    }, (error) => {
      console.warn('Real-time appointments load failed, using local storage cache:', error);
      const localApptsStr = localStorage.getItem('medlink_local_appointments');
      const localAppts = (localApptsStr ? JSON.parse(localApptsStr) : []).filter((a: any) => a.doctorId === doctorId);
      setAppointmentsCount(localAppts.length);
      const sorted = localAppts.sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
      setAppointments(sorted.slice(0, 3));
      setAppointmentsLoading(false);
    });

    return () => unsubscribe();
  }, [doctorId]);

  // Sync real-time queue
  useEffect(() => {
    if (!doctorId) {
      setQueueLoading(false);
      return;
    }
    const q = query(collection(firestoreDb, 'queue'), where('doctorId', '==', doctorId));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const liveQueue = snapshot.docs.map(doc => doc.data());
      setQueue(liveQueue);
      setQueueLoading(false);
    }, (error) => {
      console.error('Queue subscription failed on doctor dashboard:', error);
      setQueueLoading(false);
    });

    return () => unsubscribe();
  }, [doctorId]);

  const pendingPatients = queue.filter(
    (p: any) => p.status === 'Waiting' || p.status === 'waiting' || p.status === 'current'
  );

  // Active/Current patient is either status === 'current' or first Waiting
  let activePatient = queue.find((p: any) => p.status === 'current');
  if (!activePatient && pendingPatients.length > 0) {
    activePatient = pendingPatients[0];
  }

  const handleNextPatient = async () => {
    if (!activePatient) return;
    try {
      if (activePatient.status !== 'current') {
        // Mark first waiting patient as current
        await db.updateQueueStatus(activePatient.id, 'current');
      } else {
        // Finish current, find next waiting patient if any, and make them current
        await db.updateQueueStatus(activePatient.id, 'Done');
        const nextWaiting = pendingPatients.find((p: any) => p.id !== activePatient.id && p.status !== 'current');
        if (nextWaiting) {
          await db.updateQueueStatus(nextWaiting.id, 'current');
        }
      }
    } catch (err) {
      console.error('Failed to shift queue patient:', err);
    }
  };

  const handleViewPatientHistory = () => {
    if (activePatient) {
      navigate(ROUTES.PATIENT_HISTORY, { state: { patientId: activePatient.patientId } });
    } else {
      navigate(ROUTES.DOCTOR_QUEUE);
    }
  };

  const doctorName = user?.name ? (user.name.startsWith('Dr.') ? user.name : `Dr. ${user.name}`) : 'Dr. Sarah Wilson';

  // Workspace KPIs
  const kpis = [
    { label: 'Active In Queue', value: pendingPatients.length, desc: 'Realtime Waiting', icon: Users, color: 'text-blue-600 bg-blue-50 border-blue-100', path: ROUTES.DOCTOR_QUEUE },
    { label: 'Total Schedule', value: appointmentsCount, desc: 'Booked Consultations', icon: Calendar, color: 'text-emerald-600 bg-emerald-50 border-emerald-100', path: ROUTES.DOCTOR_APPOINTMENTS },
    { label: 'Registry Status', value: 'Active', desc: 'Secure HIPAA Node', icon: Shield, color: 'text-violet-600 bg-violet-50 border-violet-100' }
  ];

  return (
    <div className="min-h-screen w-full max-w-7xl mx-auto overflow-x-hidden bg-slate-50/50 pb-24 px-3 sm:px-4 lg:px-6 pt-4 sm:pt-5">
      
      {/* SaaS Premium Header with Verified Badge */}
      <section className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between mb-6">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-[9px] font-black text-emerald-600 bg-emerald-50 border border-emerald-100 px-3 py-1 rounded-full uppercase tracking-wider">
              Practitioner Node Live
            </span>
            <span className="text-[9px] font-black text-blue-600 bg-blue-50 border border-blue-100 px-3 py-1 rounded-full uppercase tracking-wider">
              MD Workspace Console
            </span>
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight leading-none mt-2.5">
            Welcome, {doctorName}
          </h1>
          <p className="text-slate-400 text-xs mt-1.5 font-bold uppercase tracking-wider flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-blue-500 inline-block animate-pulse" />
            Clinic Facility: {user?.clinicName || "HealCloud Partner Clinic"}
          </p>
        </div>

        {/* Global Practitioner Badge Profile */}
        <div className="bg-slate-900 text-white rounded-2xl p-4 flex items-center gap-4 border border-slate-800 shadow-xl max-w-sm">
          <img 
            src={user?.photoUrl || "https://images.unsplash.com/photo-1559839734-2b71f153678e?auto=format&fit=crop&q=80&w=200&h=200"}
            className="w-10 h-10 rounded-xl object-cover border border-slate-800"
            alt={doctorName}
          />
          <div>
            <p className="text-[9px] font-black text-blue-400 uppercase tracking-widest leading-none">Medical License Registration</p>
            <p className="font-mono text-xs font-black mt-1 leading-none">REG-{doctorId.toUpperCase()}</p>
          </div>
        </div>
      </section>

      {/* Practitioner KPI Deck */}
      <section className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 mb-8">
        {kpis.map((kpi, idx) => {
          const ClickableTag = kpi.path ? 'button' : 'div';
          return (
            <ClickableTag
              key={idx}
              onClick={kpi.path ? () => navigate(kpi.path) : undefined}
              className={cn(
                "bg-white p-5 rounded-3xl border border-slate-100 shadow-sm flex items-center justify-between text-left w-full transition-all duration-200",
                kpi.path ? "cursor-pointer hover:border-slate-350 hover:shadow-md active:scale-[0.99]" : ""
              )}
            >
              <div>
                <p className="text-slate-400 text-[10px] font-black uppercase tracking-wider leading-none">{kpi.label}</p>
                <p className="text-slate-900 text-2xl font-black mt-2 leading-none">{kpi.value}</p>
                <p className="text-slate-400 text-[9px] font-bold mt-1.5 leading-none">{kpi.desc}</p>
              </div>
              <div className={cn("p-3 rounded-2xl border", kpi.color)}>
                <kpi.icon className="w-6 h-6 animate-pulse-subtle" />
              </div>
            </ClickableTag>
          );
        })}
      </section>

      {/* Main SaaS Inspired Quadrants */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left Double Section: Active Patient Control Room */}
        <div className="lg:col-span-2 flex flex-col gap-6">
          
          <div className="bg-white rounded-[2rem] border border-slate-100 p-6 shadow-sm">
            <div className="flex items-center justify-between mb-6 border-b border-slate-50 pb-4">
              <div>
                <h2 className="text-sm font-black text-slate-800 uppercase tracking-wider">
                  Patient Consultation Deck
                </h2>
                <p className="text-[10px] font-bold text-slate-400 uppercase">Consulting queues dynamically stream in real-time</p>
              </div>
              <button 
                onClick={() => navigate(ROUTES.DOCTOR_QUEUE)}
                className="text-[10px] font-black text-blue-600 bg-blue-50 border border-blue-100 px-3.5 py-1.5 rounded-full uppercase tracking-wider hover:bg-blue-100 transition-colors"
              >
                Full Waiting Room
              </button>
            </div>

            {queueLoading ? (
              <div className="py-12 flex justify-center">
                <span className="w-6 h-6 border-2 border-blue-600 border-t-transparent rounded-full animate-spin" />
              </div>
            ) : activePatient ? (
              <div className="p-6 bg-slate-50/50 rounded-[2rem] border border-slate-100 relative overflow-hidden">
                <div className="absolute top-0 right-0 p-4">
                   <div className={cn(
                     "px-3 py-1 rounded-full text-[9px] font-black uppercase tracking-wider",
                     activePatient.status === 'current' ? "bg-rose-100 text-rose-600" : "bg-blue-100 text-blue-600"
                   )}>
                     {activePatient.status === 'current' ? 'consultation in progress' : 'next in line'}
                   </div>
                </div>

                <div className="flex flex-col sm:flex-row items-start sm:items-center gap-5">
                  <div className="w-20 h-20 bg-blue-600 rounded-[1.8rem] flex flex-col items-center justify-center border border-blue-700 text-white shadow-lg">
                     <span className="text-3xl font-black">{activePatient.tokenNumber || '1'}</span>
                     <span className="text-[9px] font-black text-blue-200 tracking-wider">TOKEN</span>
                  </div>
                  <div>
                    <span className="text-[9px] font-black text-slate-400 bg-slate-100 px-2 py-0.5 rounded-md uppercase tracking-widest">
                      Registered Profile Checked
                    </span>
                    <h3 className="font-black text-slate-900 text-xl tracking-tight mt-1.5">{activePatient.patientName}</h3>
                    <p className="text-slate-400 text-xs font-bold font-mono mt-1">
                      ID: ML-{activePatient.patientId?.replace('u_', '').substring(0, 8).toUpperCase() || 'PATIENT'}
                    </p>
                  </div>
                </div>

                <div className="mt-8 grid grid-cols-2 gap-4">
                  <button 
                    onClick={handleNextPatient}
                    className="py-4 bg-blue-600 hover:bg-blue-700 text-white rounded-2xl font-black text-xs uppercase tracking-widest shadow-xl shadow-blue-500/15 transition-all text-center"
                  >
                    {activePatient.status === 'current' ? 'Complete Visit' : 'Initiate Session'}
                  </button>
                  <button 
                    onClick={handleViewPatientHistory}
                    className="py-4 bg-white border border-slate-200 text-slate-700 hover:bg-slate-50 rounded-2xl font-black text-xs uppercase tracking-widest transition-all text-center"
                  >
                    Access Medical Record
                  </button>
                </div>
              </div>
            ) : (
              <div className="py-12 text-center flex flex-col items-center justify-center">
                <Clock className="w-10 h-10 text-slate-300 mb-2" />
                <h4 className="font-bold text-slate-800 text-sm">No Patients Checked In</h4>
                <p className="text-xs text-slate-400 mt-1 max-w-[240px] leading-relaxed mx-auto uppercase">
                  Check-ins from the Patient Desk will reflect automatically.
                </p>
              </div>
            )}
          </div>

          {/* Upcoming Booked Appointments Section */}
          <div className="bg-white rounded-[2rem] border border-slate-100 p-6 shadow-sm flex flex-col">
            <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-50">
              <div>
                <h2 className="text-sm font-black text-slate-800 uppercase tracking-wider">
                  Upcoming Booked Appointments
                </h2>
                <p className="text-[10px] font-bold text-slate-400 uppercase leading-none mt-1">Consultations reserved from the Patient portal</p>
              </div>
              <button 
                onClick={() => navigate(ROUTES.DOCTOR_APPOINTMENTS)}
                className="text-[10px] font-black text-blue-600 bg-blue-50 border border-blue-100 px-3.5 py-1.5 rounded-full uppercase tracking-wider hover:bg-blue-100 transition-colors"
              >
                All ({appointmentsCount})
              </button>
            </div>

            {appointmentsLoading ? (
              <div className="py-8 flex justify-center">
                <span className="w-5 h-5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
              </div>
            ) : appointments.length === 0 ? (
              <div className="py-8 text-center flex flex-col items-center justify-center text-slate-400">
                <Calendar className="w-8 h-8 text-slate-300 mb-2 animate-pulse" />
                <p className="text-xs font-bold uppercase tracking-wide leading-none">No booked appointments found</p>
                <p className="text-[10px] uppercase font-bold tracking-wider text-slate-300 mt-1">Patient bookings will automatically synchronize here</p>
              </div>
            ) : (
              <div className="flex flex-col gap-3">
                {appointments.map((app) => (
                  <div 
                    key={app.id}
                    className="p-4 bg-slate-50/50 hover:bg-slate-50 rounded-2xl border border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3 transition-colors"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-blue-50 rounded-xl flex items-center justify-center text-blue-600 font-extrabold text-xs shrink-0 uppercase">
                        {app.patientName ? app.patientName.charAt(0).toUpperCase() : 'P'}
                      </div>
                      <div>
                        <h4 className="font-extrabold text-slate-900 text-xs">{app.patientName || `Patient`}</h4>
                        <div className="flex items-center gap-2 mt-0.5 text-[9px] font-bold text-slate-400 uppercase tracking-wider">
                          <span className="text-blue-500 text-[10px]">{app.type || 'Consultation'}</span>
                          <span>•</span>
                          <span>{app.date}</span>
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-3 self-end sm:self-auto">
                      <div className="px-3 py-1.5 bg-blue-900 text-white rounded-xl text-[9px] font-black uppercase tracking-wider flex items-center gap-1.5">
                        <Clock className="w-3 h-3" />
                        {app.time}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

        </div>

        {/* Right Section: Clinic Continuity & Collaboration Platform */}
        <div className="lg:col-span-1 flex flex-col gap-6">
          
          <div className="bg-white rounded-[2rem] border border-slate-100 p-6 shadow-sm flex flex-col justify-between h-full min-h-[300px]">
            <div>
              <h2 className="text-xs font-black text-slate-400 uppercase tracking-widest mb-4">
                Facility Continuity & Network
              </h2>
              
              <div className="flex flex-col gap-3">
                <button 
                  onClick={() => navigate(ROUTES.DOCTOR_LEAVE_APPLY)}
                  className="w-full p-4 bg-slate-50 hover:bg-slate-100 border border-slate-100 rounded-2xl flex items-center gap-4 transition-colors text-left"
                >
                  <div className="p-3 bg-amber-500 text-white rounded-xl">
                    <Building className="w-5 h-5" />
                  </div>
                  <div>
                    <h4 className="font-black text-xs text-slate-900 uppercase">Request Coverage</h4>
                    <p className="text-[10px] text-slate-400 font-bold uppercase mt-0.5">Delegate active patient streams</p>
                  </div>
                  <ChevronRight className="w-4 h-4 text-slate-400 ml-auto" />
                </button>

                <button 
                  onClick={() => navigate(ROUTES.DOCTOR_COVERAGE_RECEIVED)}
                  className="w-full p-4 bg-slate-50 hover:bg-slate-100 border border-slate-100 rounded-2xl flex items-center gap-4 transition-colors text-left"
                >
                  <div className="p-3 bg-blue-500 text-white rounded-xl">
                    <Clock className="w-5 h-5" />
                  </div>
                  <div>
                    <h4 className="font-black text-xs text-slate-900 uppercase">Cross-Network Shift</h4>
                    <p className="text-[10px] text-slate-400 font-bold uppercase mt-0.5">Manage standby clinic coverages</p>
                  </div>
                  <ChevronRight className="w-4 h-4 text-slate-400 ml-auto" />
                </button>
              </div>
            </div>

            {/* Premium Custom Prescription Shortcut Panel */}
            <div 
              onClick={() => navigate(ROUTES.DOCTOR_PRESCRIPTION_WRITE)}
              className="mt-6 p-4 bg-gradient-to-br from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 text-white rounded-3xl shadow-lg relative overflow-hidden group cursor-pointer active:scale-95 transition-all"
            >
              <div className="absolute top-0 right-0 p-3 opacity-15">
                <FileText className="w-16 h-16" />
              </div>
              <div className="flex items-center gap-3 relative z-10">
                <div className="p-2.5 bg-white/10 rounded-xl">
                  <FileText className="w-5 h-5" />
                </div>
                <div>
                  <h4 className="font-black text-xs uppercase tracking-widest text-blue-200">Physician Rx pad</h4>
                  <p className="font-bold text-[11px] text-slate-100 mt-1">Dispense HIPAA prescriptions</p>
                </div>
                <ChevronRight className="w-4 h-4 text-white/60 ml-auto" />
              </div>
            </div>

          </div>

        </div>

      </div>

      {/* Security Shared Patient Access Request Notification Banner */}
      <section className="mt-8">
        <div className="p-4 bg-amber-500/10 border border-amber-500/20 rounded-3xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-amber-500 text-white rounded-2xl shadow-md flex-shrink-0">
              <Shield className="w-5 h-5 animate-pulse" />
            </div>
            <div className="min-w-0">
              <h4 className="font-black text-slate-900 text-xs uppercase leading-none">External Practitioner Registry Query</h4>
              <p className="text-slate-500 text-[10px] uppercase font-bold tracking-wide mt-1">
                Dr. James Chen requested dynamic access to 2 patient care archives
              </p>
            </div>
          </div>
          <button 
            onClick={() => navigate(ROUTES.DOCTOR_ACCESS_REQUESTS)}
            className="w-full sm:w-auto px-4 py-2.5 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-[9px] font-black uppercase tracking-widest transition-all text-center"
          >
            Review Query
          </button>
        </div>
      </section>

    </div>
  );
}

