export enum UserRole {
  DOCTOR = 'doctor',
  PATIENT = 'patient',
}

export interface User {
  id: string;
  role: UserRole;
  name: string;
  email: string;
  photoUrl?: string;
}

export interface Doctor extends User {
  specialty: string;
  clinicName: string;
  bio: string;
  rating: number;
  reviewCount: number;
  isVerified: boolean;
}

export interface Patient extends User {
  age?: number;
  bloodGroup?: string;
}

export interface Appointment {
  id: string;
  doctorId: string;
  patientId: string;
  date: string;
  time: string;
  status: 'pending' | 'confirmed' | 'completed' | 'cancelled';
  tokenNumber: number;
  reason?: string;
}

export interface Medicine {
  name: string;
  dosage: string;
  frequency: string;
  duration: string;
}

export interface Prescription {
  id: string;
  appointmentId: string;
  doctorId: string;
  patientId: string;
  date: string;
  medicines: Medicine[];
  instructions?: string;
}

export interface Reminder {
  id: string;
  patientId: string;
  medicineName: string;
  time: string;
  status: 'pending' | 'taken' | 'skipped';
  date: string;
}

export interface LeaveRequest {
  id: string;
  doctorId: string;
  startDate: string;
  endDate: string;
  reason: string;
  status: 'pending' | 'approved' | 'rejected';
  substituteDoctorId?: string;
}

export interface CoverageRequest {
  id: string;
  fromDoctorId: string;
  toDoctorId: string;
  leaveRequestId: string;
  status: 'pending' | 'accepted' | 'rejected';
}

export interface Review {
  id: string;
  doctorId: string;
  patientId: string;
  rating: number;
  comment: string;
  date: string;
}

export interface PatientAccess {
  doctorId: string;
  patientId: string;
  status: 'pending' | 'granted' | 'denied';
}
