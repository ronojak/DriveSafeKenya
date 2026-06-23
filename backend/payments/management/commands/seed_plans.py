from django.core.management.base import BaseCommand
from payments.models import PaymentPlan

PLANS = [
    {
        "code": "weekly",
        "name": "Weekly",
        "amount": 100,
        "duration_days": 7,
        "description": "7 days of premium access",
    },
    {
        "code": "monthly",
        "name": "Monthly",
        "amount": 300,
        "duration_days": 30,
        "description": "30 days of premium access",
    },
    {
        "code": "yearly",
        "name": "Yearly",
        "amount": 2000,
        "duration_days": 365,
        "description": "365 days of premium access",
    },
]


class Command(BaseCommand):
    help = "Seed payment plans"

    def handle(self, *args, **options):
        for plan_data in PLANS:
            _, created = PaymentPlan.objects.update_or_create(
                code=plan_data["code"],
                defaults=plan_data,
            )
            status = "Created" if created else "Updated"
            self.stdout.write(f"  {status}: {plan_data['name']} — KES {plan_data['amount']}")
        self.stdout.write(self.style.SUCCESS(f"Done: {len(PLANS)} plans seeded"))
