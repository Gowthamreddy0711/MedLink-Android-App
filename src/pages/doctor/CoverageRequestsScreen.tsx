import { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { useNavigate } from 'react-router-dom';
import { 
  Users, 
  MapPin, 
  ArrowLeft, 
  CheckCircle2, 
  XCircle, 
  Clock, 
  Calendar,
  ChevronRight,
  Stethoscope
} from 'lucide-react';
import { ROUTES } from '../../constants';
import { cn } from '../../lib/utils';

export default function CoverageRequestsScreen() {
  const navigate = useNavigate();
  const [requests, setRequests] = useState([
    {
      id: 'cov1',
      doctorName: 'Dr. Sarah Wilson',
      specialty: 'Cardiologist',
      date: 'Oct 15, 2023',
      timeSlot: '09:00 AM - 01:00 PM',
      clinic: 'Wilson Heart Center',
      status: 'pending',
      photoUrl: 'https://images.unsplash.com/photo-1559839734-2b71f153678e?auto=format&fit=crop&q=80&w=200&h=200'
    }
  ]);

  const handleAction = (id: string, newStatus: 'approved' | 'rejected') => {
    setRequests(prev => prev.map(req => 
      req.id === id ? { ...req, status: newStatus } : req
    ));
    
    // Simulate API call
    setTimeout(() => {
      setRequests(prev => prev.filter(req => req.id !== id));
    }, 100);
  };

  return (
    <div className="min-h-screen overflow-x-hidden bg-slate-50 px-4 py-4 pb-24 sm:px-6 sm:py-6">
      <header className="mx-auto mb-6 flex w-full max-w-6xl items-center justify-between sm:mb-8">
        <h1 className="text-2xl font-black text-blue-900 uppercase tracking-tight leading-tight">Coverage Requests</h1>
        <button onClick={() => navigate(-1)} className="p-3 bg-white rounded-2xl shadow-sm">
          <ArrowLeft className="w-6 h-6 text-blue-900" />
        </button>
      </header>

      <div className="mx-auto flex w-full max-w-6xl flex-col gap-6">
        <AnimatePresence mode="popLayout">
          {requests.map((request) => (
            <motion.div
              key={request.id}
              layout
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.9, opacity: 0 }}
              className="bg-white rounded-[2.5rem] border border-slate-100 shadow-sm overflow-hidden"
            >
              {request.status === 'pending' ? (
                <div className="p-6">
                  <div className="flex items-center gap-4 mb-6">
                    <img 
                      src={request.photoUrl} 
                      className="w-16 h-16 rounded-2xl object-cover"
                      alt={request.doctorName}
                    />
                    <div>
                      <h3 className="font-black text-blue-950 text-lg">{request.doctorName}</h3>
                      <p className="text-slate-500 text-xs font-bold uppercase tracking-widest">{request.specialty}</p>
                    </div>
                  </div>

                  <div className="flex flex-col gap-4 mb-6">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-slate-50 rounded-xl flex items-center justify-center text-slate-400">
                        <Calendar className="w-5 h-5" />
                      </div>
                      <div>
                        <p className="text-[10px] font-black text-slate-400 uppercase tracking-tight">Date</p>
                        <p className="text-sm font-bold text-blue-900">{request.date}</p>
                      </div>
                    </div>

                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-slate-50 rounded-xl flex items-center justify-center text-slate-400">
                        <Clock className="w-5 h-5" />
                      </div>
                      <div>
                        <p className="text-[10px] font-black text-slate-400 uppercase tracking-tight">Time Slot</p>
                        <p className="text-sm font-bold text-blue-900">{request.timeSlot}</p>
                      </div>
                    </div>

                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-slate-50 rounded-xl flex items-center justify-center text-slate-400">
                        <MapPin className="w-5 h-5" />
                      </div>
                      <div>
                        <p className="text-[10px] font-black text-slate-400 uppercase tracking-tight">Location</p>
                        <p className="text-sm font-bold text-blue-900">{request.clinic}</p>
                      </div>
                    </div>
                  </div>

                  <div className="flex gap-3">
                    <button 
                      onClick={() => handleAction(request.id, 'approved')}
                      className="flex-1 py-4 bg-blue-600 text-white rounded-2xl font-black text-[10px] uppercase tracking-widest shadow-lg shadow-blue-600/20 active:scale-[0.98] transition-all flex items-center justify-center gap-2"
                    >
                      <CheckCircle2 className="w-4 h-4" />
                      Accept
                    </button>
                    <button 
                      onClick={() => handleAction(request.id, 'rejected')}
                      className="flex-1 py-4 bg-slate-100 text-slate-500 rounded-2xl font-black text-[10px] uppercase tracking-widest active:scale-[0.98] transition-all flex items-center justify-center gap-2"
                    >
                      <XCircle className="w-4 h-4" />
                      Decline
                    </button>
                  </div>
                </div>
              ) : (
                <div className="p-10 flex flex-col items-center justify-center text-center">
                  <div className={cn(
                    "w-16 h-16 rounded-[2rem] flex items-center justify-center mb-4",
                    request.status === 'approved' ? 'bg-emerald-50 text-emerald-500' : 'bg-rose-50 text-rose-500'
                  )}>
                    {request.status === 'approved' ? <CheckCircle2 className="w-8 h-8" /> : <XCircle className="w-8 h-8" />}
                  </div>
                  <h4 className="font-black text-blue-950">
                    Coverage Request {request.status === 'approved' ? 'Accepted' : 'Declined'}
                  </h4>
                  <p className="text-slate-400 text-xs font-medium mt-1">
                    Your schedule is being updated...
                  </p>
                </div>
              )}
            </motion.div>
          ))}
        </AnimatePresence>

        {requests.length === 0 && (
          <div className="flex flex-col items-center justify-center py-20 text-center opacity-40">
            <div className="w-20 h-20 bg-slate-100 rounded-[2.5rem] flex items-center justify-center mb-6">
              <Stethoscope className="w-10 h-10 text-slate-400" />
            </div>
            <h3 className="font-black text-blue-900 uppercase tracking-tight">No Requests</h3>
            <p className="text-sm font-medium text-slate-500 max-w-[200px] mt-2">
              You haven't received any coverage requests yet.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
