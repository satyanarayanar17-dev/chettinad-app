#!/usr/bin/env python3
"""Build locally with a protected upload key; never put a key or password in Git/CI artifacts."""
from pathlib import Path
import argparse,json,os,subprocess,urllib.request
parser=argparse.ArgumentParser()
parser.add_argument('--environment',choices=['demo','production'],required=True)
parser.add_argument('--credentials',type=Path,required=True)
parser.add_argument('--api-url')
parser.add_argument('--expected-backend-commit')
parser.add_argument('--gradle',default='gradle')
parser.add_argument('--approve-live-backend',action='store_true')
args=parser.parse_args()
repo=Path(__file__).resolve().parents[1]
key=json.loads(args.credentials.read_text())
env=dict(os.environ,KEYSTORE_PATH=key['store_path'],STORE_PASSWORD=key['password'],KEY_PASSWORD=key['password'])
command=[args.gradle,':app-patient:bundle'+args.environment.title()+'Release',':app-staff:bundle'+args.environment.title()+'Release',':app-patient:assemble'+args.environment.title()+'Release',':app-staff:assemble'+args.environment.title()+'Release','-PPRIVACY_POLICY_URL=https://chettinad-care-web.onrender.com/privacy.html','-PSUPPORT_URL=https://chettinad-care-web.onrender.com/support.html']
if args.environment=='production':
 if not args.approve_live_backend or not args.api_url or not args.expected_backend_commit:parser.error('Verify the allocated clinical API, real handset OTP and recovery before approving the production build.')
 if not args.api_url.startswith('https://') or not args.api_url.endswith('/api/v1/'):parser.error('An owned HTTPS API URL ending /api/v1/ is required.')
 with urllib.request.urlopen(args.api_url+'auth/opd/config',timeout=20) as response:configuration=json.load(response)
 if configuration.get('demo_otp') is not False or configuration.get('sms_available') is not True or configuration.get('doctor_registration_required') is not True:raise SystemExit('The backend is not configured for real doctor-provisioned SMS access.')
 with urllib.request.urlopen(args.api_url+'health',timeout=20) as response:health=json.load(response)
 if health.get('app_env')!='restricted_web_pilot' or health.get('db')!='postgres' or health.get('release',{}).get('commit')!=args.expected_backend_commit:raise SystemExit('The allocated clinical API must run the reviewed release with the restricted PostgreSQL profile.')
 with urllib.request.urlopen(args.api_url+'ready',timeout=20) as response:readiness=json.load(response)
 if readiness.get('ready') is not True:raise SystemExit('The clinical backend is not ready.')
 for url in ['https://chettinad-care-web.onrender.com/privacy.html','https://chettinad-care-web.onrender.com/support.html']:
  with urllib.request.urlopen(url,timeout=20) as response:
   if response.status!=200:raise SystemExit('A required public policy page is unavailable.')
 command+=['-PPRODUCTION_API_URL='+args.api_url,'-PPILOT_BACKEND_APPROVED=true']
raise SystemExit(subprocess.call(command,cwd=repo,env=env))
